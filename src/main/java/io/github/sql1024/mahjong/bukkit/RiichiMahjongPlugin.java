package io.github.sql1024.mahjong.bukkit;

import io.github.sql1024.mahjong.core.Rules;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** 日本麻將 (立直麻將) 的 Paper 外掛。 */
public final class RiichiMahjongPlugin extends JavaPlugin {

    private GameManager manager;
    private Rules defaultRules = Rules.defaults();
    private long botDelayMillis = 1000L;
    private long roundBreakMillis = 8000L;
    private boolean logDiscards = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        manager = new GameManager(this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register("mahjong", "日本麻將 (立直麻將)", List.of("mj", "riichi"),
                    new MahjongCommand(this));
        });
        getLogger().info("日本麻將已啟用，輸入 /mj help 查看用法。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    /** 重新讀取 config.yml。 */
    public void reloadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        defaultRules = new Rules(
                config.getInt("rules.aka-dora", 3),
                config.getBoolean("rules.kuitan", true),
                config.getBoolean("rules.kazoe-yakuman", true),
                config.getBoolean("rules.double-yakuman", true),
                config.getBoolean("rules.kiriage-mangan", false),
                config.getInt("rules.starting-points", 25000),
                config.getInt("rules.rounds", 2),
                config.getBoolean("rules.abortive-draws", true),
                config.getBoolean("rules.multiple-ron", true),
                config.getBoolean("rules.tenpai-renchan", true),
                config.getBoolean("rules.end-on-bankrupt", true),
                config.getInt("rules.turn-seconds", 20));
        botDelayMillis = config.getLong("bots.delay-millis", 1000L);
        roundBreakMillis = config.getLong("game.round-break-millis", 8000L);
        logDiscards = config.getBoolean("game.log-discards", true);
    }

    public GameManager manager() {
        return manager;
    }

    public Rules defaultRules() {
        return defaultRules;
    }

    public long botDelayMillis() {
        return botDelayMillis;
    }

    public long roundBreakMillis() {
        return roundBreakMillis;
    }

    public boolean logDiscards() {
        return logDiscards;
    }
}
