package com.majong.riichi.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Keeps track of the tables on the server and who is sitting at which. */
public final class TableManager {

    private final MahjongPlugin plugin;
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<UUID, String> seated = new HashMap<>();

    TableManager(MahjongPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates a table with the given player as its first occupant. */
    public Table create(Player owner, String name) {
        String key = name.toLowerCase();
        if (tables.containsKey(key) || seated.containsKey(owner.getUniqueId())) {
            return null;
        }
        Table table = new Table(plugin, name, owner);
        tables.put(key, table);
        seated.put(owner.getUniqueId(), key);
        return table;
    }

    public Table get(String name) {
        return tables.get(name.toLowerCase());
    }

    public Table tableOf(Player player) {
        String key = seated.get(player.getUniqueId());
        return key == null ? null : tables.get(key);
    }

    /** Seats a player at an existing table; returns their seat or {@code -1}. */
    public int join(Player player, Table table) {
        if (seated.containsKey(player.getUniqueId()) || table.state() != Table.State.LOBBY) {
            return -1;
        }
        int seat = table.addPlayer(player);
        if (seat >= 0) {
            seated.put(player.getUniqueId(), table.name().toLowerCase());
        }
        return seat;
    }

    /** Removes a player from whatever table they are at. */
    public Table leave(Player player) {
        String key = seated.remove(player.getUniqueId());
        if (key == null) {
            return null;
        }
        Table table = tables.get(key);
        if (table == null) {
            return null;
        }
        table.remove(player.getUniqueId());
        if (table.humanPlayers().isEmpty()) {
            table.shutdown();
            tables.remove(key);
        }
        return table;
    }

    public void close(Table table) {
        table.shutdown();
        tables.remove(table.name().toLowerCase());
        seated.values().removeIf(key -> key.equals(table.name().toLowerCase()));
    }

    public Collection<Table> tables() {
        return List.copyOf(tables.values());
    }

    public List<String> names() {
        return new ArrayList<>(tables.keySet());
    }

    void shutdownAll() {
        for (Table table : tables.values()) {
            table.shutdown();
        }
        tables.clear();
        seated.clear();
    }
}
