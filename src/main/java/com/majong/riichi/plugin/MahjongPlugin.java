package com.majong.riichi.plugin;

import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.GameRules;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private GameRules rules;
    private int turnTimeoutTicks = 20 * 30;
    private int botDelayTicks = 12;
    private int nextHandDelayTicks = 20 * 6;

    private TileRenderer.Style defaultStyle = TileRenderer.Style.AUTO;
    private String packUrl = "";
    private byte[] packHash;
    private boolean packRequired;

    private final Map<UUID, TileRenderer.Style> styleOverrides = new HashMap<>();
    private final Set<UUID> packLoaded = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readSettings();
        tables = new TableManager(this);
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
        getSLF4JLogger().info("Riichi mahjong ready; type /mj help to sit down.");
    }

    @Override
    public void onDisable() {
        if (tables != null) {
            tables.shutdownAll();
        }
    }

    private void readSettings() {
        FileConfiguration config = getConfig();
        turnTimeoutTicks = 20 * Math.max(5, config.getInt("turn-timeout-seconds", 30));
        botDelayTicks = Math.max(1, config.getInt("bot-delay-ticks", 12));
        nextHandDelayTicks = 20 * Math.max(1, config.getInt("next-hand-delay-seconds", 6));

        defaultStyle = TileRenderer.Style.parse(config.getString("tile-graphics", "auto"));
        packUrl = config.getString("resource-pack.url", "");
        packHash = ResourcePackFile.decodeHash(config.getString("resource-pack.sha1", ""));
        packRequired = config.getBoolean("resource-pack.required", false);

        boolean eastOnly = "tonpuusen".equalsIgnoreCase(config.getString("rules.game-length", "hanchan"));
        rules = new GameRules(
                config.getInt("rules.starting-points", 25000),
                eastOnly ? Tiles.EAST : Tiles.SOUTH,
                config.getBoolean("rules.red-fives", true),
                config.getBoolean("rules.open-tanyao", true),
                config.getBoolean("rules.head-bump", true),
                config.getBoolean("rules.end-on-bankruptcy", true));
    }

    /**
     * Writes the tile resource pack next to the config so an admin has a file to
     * host, and logs the hash they need to paste into config.yml.
     */
    private void exportResourcePack() {
        try {
            Path file = ResourcePackFile.write(this);
            getSLF4JLogger().info("Tile resource pack written to {} (sha1 {})",
                    file, ResourcePackFile.sha1(file));
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
        event.getPlayer().setResourcePack(packUrl, packHash,
                Component.text("麻雀の牌を表示するためのリソースパックです", NamedTextColor.GOLD),
                packRequired);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
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
