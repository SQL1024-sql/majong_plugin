package io.github.sql1024.mahjong.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 處理 GUI 點擊與玩家上下線。 */
public final class GameListener implements Listener {

    private final RiichiMahjongPlugin plugin;

    public GameListener(RiichiMahjongPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TableGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        gui.game().handleClick(player, gui, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TableGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MahjongGame game = plugin.manager().gameOf(event.getPlayer().getUniqueId());
        if (game != null) {
            game.handleQuit(event.getPlayer().getUniqueId());
            return;
        }
        GameManager.Lobby lobby = plugin.manager().lobbyOf(event.getPlayer().getUniqueId());
        if (lobby != null) {
            plugin.manager().leave(lobby, event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MahjongGame game = plugin.manager().gameOf(event.getPlayer().getUniqueId());
        if (game != null) {
            game.handleRejoin(event.getPlayer());
        }
    }
}
