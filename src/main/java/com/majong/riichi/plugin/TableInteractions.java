package com.majong.riichi.plugin;

import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.Action;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Turns a right click on a tile standing at a table into a discard. */
public final class TableInteractions implements Listener {

    private final MahjongPlugin plugin;
    private final NamespacedKey tileKey;
    private final NamespacedKey seatKey;

    TableInteractions(MahjongPlugin plugin) {
        this.plugin = plugin;
        this.tileKey = new NamespacedKey(plugin, "table_tile");
        this.seatKey = new NamespacedKey(plugin, "table_seat");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        PersistentDataContainer data = event.getRightClicked().getPersistentDataContainer();
        String notation = data.get(tileKey, PersistentDataType.STRING);
        Integer seat = data.get(seatKey, PersistentDataType.INTEGER);
        if (notation == null || seat == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        Table table = plugin.tables().tableOf(player);
        if (table == null || table.state() != Table.State.PLAYING) {
            return;
        }
        if (table.seatOf(player.getUniqueId()) != seat) {
            // Somebody else's tiles; the faces are hidden from them anyway.
            return;
        }
        Tile tile = Tile.parse(notation);
        if (!table.submit(seat, new Action.Discard(tile, false))) {
            player.sendMessage(Component.text()
                    .append(Component.text("[麻雀] ", NamedTextColor.GOLD))
                    .append(Component.text("現在不能打 " + Tiles.display(tile.kind()),
                            NamedTextColor.RED))
                    .build());
        }
    }
}
