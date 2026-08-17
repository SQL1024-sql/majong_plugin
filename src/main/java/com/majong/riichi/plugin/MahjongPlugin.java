package com.majong.riichi.plugin;

import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.GameRules;
import com.majong.riichi.game.Variant;
import com.majong.riichi.taiwan.TaiwanRules;
import com.majong.riichi.plugin.scene.TableScene;
import com.majong.riichi.plugin.scene.TilePreview;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point: loads the settings and wires up the command. */
public final class MahjongPlugin extends JavaPlugin implements Listener {

    private TableManager tables;
    private TilePreview preview;
    private GameRules rules;
    private GameRules taiwanGameRules;
    private Variant defaultVariant = Variant.JAPANESE;
    private TaiwanRules taiwanStakes = TaiwanRules.standard();
    private int turnTimeoutTicks = 20 * 30;
    private int botDelayTicks = 12;
    private int nextHandDelayTicks = 20 * 6;
    private boolean tableScene = true;
    private double tableRadius = 0.9;
    private double tableHeight = 0.9;
    private float tileScale = 0.5f;
    private double tileSpacing = 0.22;

    /**
     * A fixed id for our pack. Servers may send several packs at once, and the
     * id is what keeps ours apart from everybody else's, both when sending it
     * and when reading the status the client reports back.
     */
    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("majong-tiles".getBytes(StandardCharsets.UTF_8));
    /** The client rejects a pack hash that is not exactly twenty bytes. */
    private static final int SHA1_LENGTH = 20;

    private TileRenderer.Style defaultStyle = TileRenderer.Style.AUTO;
    private String packUrl = "";
    private byte[] packHash;
    private boolean packRequired;
    private String packPrompt = "";

    private final Map<UUID, TileRenderer.Style> styleOverrides = new HashMap<>();
    private final Set<UUID> packLoaded = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readSettings();
        tables = new TableManager(this);
        preview = new TilePreview(this);
        exportResourcePack();

        PluginCommand command = getCommand("mahjong");
        if (command == null) {
            getSLF4JLogger().error("the mahjong command is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MahjongCommand executor = new MahjongCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(preview, this);
        getServer().getPluginManager().registerEvents(new TableInteractions(this), this);
        // Console logs stay in ascii: a server console that is not on UTF-8 turns
        // chinese into question marks. Everything players see is a chat component,
        // which is sent as UTF-8 and always renders.
        getSLF4JLogger().info("Mahjong ready; type /mj help to sit down.");
    }

    @Override
    public void onDisable() {
        if (tables != null) {
            tables.shutdownAll();
        }
        if (preview != null) {
            preview.clearAll();
        }
    }

    private void readSettings() {
        FileConfiguration config = getConfig();
        turnTimeoutTicks = 20 * Math.max(5, config.getInt("turn-timeout-seconds", 30));
        botDelayTicks = Math.max(1, config.getInt("bot-delay-ticks", 12));
        nextHandDelayTicks = 20 * Math.max(1, config.getInt("next-hand-delay-seconds", 6));

        tableScene = config.getBoolean("table.enabled", true);
        tableRadius = config.getDouble("table.radius", 0.9);
        tableHeight = config.getDouble("table.height", 0.9);
        tileScale = (float) config.getDouble("table.tile-scale", 0.5);
        tileSpacing = config.getDouble("table.tile-spacing", 0.22);

        defaultStyle = TileRenderer.Style.parse(config.getString("tile-graphics", "auto"));
        packUrl = config.getString("resource-pack.url", "").trim();
        packRequired = config.getBoolean("resource-pack.required", false);
        packPrompt = config.getString("resource-pack.prompt", "這是顯示麻將牌面用的資源包");
        packHash = ResourcePackFile.decodeHash(config.getString("resource-pack.sha1", ""));
        if (packHash != null && packHash.length != SHA1_LENGTH) {
            getSLF4JLogger().warn("resource-pack.sha1 is not a 40 character sha1; ignoring it. "
                    + "Without it the client re-downloads the pack every time.");
            packHash = null;
        }

        defaultVariant = Variant.parse(config.getString("variant", "japan"));
        boolean eastOnly = "tonpuusen".equalsIgnoreCase(config.getString("rules.game-length", "hanchan"));
        int finalWind = eastOnly ? Tiles.EAST : Tiles.SOUTH;
        boolean headBump = config.getBoolean("rules.head-bump", true);
        rules = new GameRules(Variant.JAPANESE,
                config.getInt("rules.starting-points", 25000),
                finalWind,
                config.getBoolean("rules.red-fives", true),
                config.getBoolean("rules.open-tanyao", true),
                headBump,
                config.getBoolean("rules.end-on-bankruptcy", true));
        taiwanGameRules = new GameRules(Variant.TAIWANESE,
                config.getInt("taiwan.starting-points", 0),
                finalWind, false, true, headBump,
                config.getBoolean("taiwan.end-on-bankruptcy", false));
        taiwanStakes = new TaiwanRules(
                config.getInt("taiwan.base", TaiwanRules.DEFAULT_BASE),
                config.getInt("taiwan.per-tai", TaiwanRules.DEFAULT_PER_TAI),
                readTaiOverrides(config));
    }

    /** Reads any per pattern tai values an admin has overridden. */
    private java.util.Map<com.majong.riichi.taiwan.Tai, Integer> readTaiOverrides(
            FileConfiguration config) {
        java.util.Map<com.majong.riichi.taiwan.Tai, Integer> overrides =
                new java.util.EnumMap<>(com.majong.riichi.taiwan.Tai.class);
        var section = config.getConfigurationSection("taiwan.tai");
        if (section == null) {
            return overrides;
        }
        for (com.majong.riichi.taiwan.Tai tai : com.majong.riichi.taiwan.Tai.values()) {
            if (section.isInt(tai.configKey())) {
                overrides.put(tai, section.getInt(tai.configKey()));
            }
        }
        return overrides;
    }

    /**
     * Writes the tile resource pack next to the config so an admin has a file to
     * host, and logs the hash they need to paste into config.yml.
     */
    private void exportResourcePack() {
        try {
            Path file = ResourcePackFile.write(this);
            // The size and count are here because the usual failure is a client
            // still holding an older copy of the pack.
            getSLF4JLogger().info("Tile resource pack written to {} ({} files, {} bytes, sha1 {})",
                    file, ResourcePackFile.entryCount(this), java.nio.file.Files.size(file),
                    ResourcePackFile.sha1(file));
        } catch (IOException exception) {
            getSLF4JLogger().warn("could not write the tile resource pack", exception);
        }
    }

    /** Re-reads config.yml; tables already running keep the rules they started with. */
    public void reload() {
        reloadConfig();
        readSettings();
    }

    public TableManager tables() {
        return tables;
    }

    public GameRules rules() {
        return rules;
    }

    /** The rules for the given game, so a table can pick which one it deals. */
    public GameRules rulesFor(Variant variant) {
        return variant == Variant.TAIWANESE ? taiwanGameRules : rules;
    }

    /** The game a table is dealt when nobody says which. */
    public Variant defaultVariant() {
        return defaultVariant;
    }

    public TaiwanRules taiwanStakes() {
        return taiwanStakes;
    }

    public TilePreview preview() {
        return preview;
    }

    /** A renderer for one table, using the sizes from config.yml. */
    public TableScene createScene() {
        return new TableScene(this, tableRadius, tableHeight, tileScale, tileSpacing);
    }

    /** Whether tables should be drawn in the world at all. */
    public boolean isTableSceneEnabled() {
        return tableScene;
    }

    public int turnTimeoutTicks() {
        return turnTimeoutTicks;
    }

    public int botDelayTicks() {
        return botDelayTicks;
    }

    public int nextHandDelayTicks() {
        return nextHandDelayTicks;
    }

    // -------------------------------------------------------- tile rendering

    /** How this player's tiles should be drawn right now. */
    public TileRenderer rendererFor(Player player) {
        return switch (styleFor(player)) {
            case TEXT -> TileRenderer.TEXT;
            case TILES -> TileRenderer.GLYPH;
            case AUTO -> packLoaded.contains(player.getUniqueId())
                    ? TileRenderer.GLYPH : TileRenderer.TEXT;
        };
    }

    public TileRenderer.Style styleFor(Player player) {
        return styleOverrides.getOrDefault(player.getUniqueId(), defaultStyle);
    }

    /** Remembers a player's own choice for the rest of the session. */
    public void setStyle(Player player, TileRenderer.Style style) {
        styleOverrides.put(player.getUniqueId(), style);
    }

    public boolean hasResourcePack(Player player) {
        return packLoaded.contains(player.getUniqueId());
    }

    public boolean isResourcePackConfigured() {
        return !packUrl.isBlank();
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (packUrl.isBlank()) {
            return;
        }
        // Added rather than set: setting one switches the client to ours alone
        // and would throw away whatever pack the server already sends.
        event.getPlayer().addResourcePack(PACK_ID, packUrl, packHash, packPrompt, packRequired);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            // Some other pack on the same client; none of our business.
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            packLoaded.add(uuid);
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                || event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            packLoaded.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tables.leave(event.getPlayer());
        packLoaded.remove(event.getPlayer().getUniqueId());
        styleOverrides.remove(event.getPlayer().getUniqueId());
    }
}
