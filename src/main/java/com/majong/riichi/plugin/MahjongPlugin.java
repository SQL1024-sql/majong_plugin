package com.majong.riichi.plugin;

import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.GameRules;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point: loads the settings and wires up the command. */
public final class MahjongPlugin extends JavaPlugin implements Listener {

    private TableManager tables;
    private GameRules rules;
    private int turnTimeoutTicks = 20 * 30;
    private int botDelayTicks = 12;
    private int nextHandDelayTicks = 20 * 6;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readSettings();
        tables = new TableManager(this);

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

        boolean eastOnly = "tonpuusen".equalsIgnoreCase(config.getString("rules.game-length", "hanchan"));
        rules = new GameRules(
                config.getInt("rules.starting-points", 25000),
                eastOnly ? Tiles.EAST : Tiles.SOUTH,
                config.getBoolean("rules.red-fives", true),
                config.getBoolean("rules.open-tanyao", true),
                config.getBoolean("rules.head-bump", true),
                config.getBoolean("rules.end-on-bankruptcy", true));
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tables.leave(event.getPlayer());
    }
}
