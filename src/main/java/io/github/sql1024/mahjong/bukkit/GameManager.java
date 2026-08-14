package io.github.sql1024.mahjong.bukkit;

import io.github.sql1024.mahjong.core.Rules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 管理等待中的牌桌與進行中的對局。 */
public final class GameManager {

    /** 還在等人的牌桌。 */
    public static final class Lobby {
        private final String id;
        private final UUID owner;
        private final List<UUID> players = new ArrayList<>();
        private Rules rules;

        Lobby(String id, UUID owner, Rules rules) {
            this.id = id;
            this.owner = owner;
            this.rules = rules;
            this.players.add(owner);
        }

        public String id() {
            return id;
        }

        public UUID owner() {
            return owner;
        }

        public List<UUID> players() {
            return players;
        }

        public Rules rules() {
            return rules;
        }

        public void setRules(Rules rules) {
            this.rules = rules;
        }
    }

    private final RiichiMahjongPlugin plugin;
    private final Map<String, Lobby> lobbies = new LinkedHashMap<>();
    private final Map<String, MahjongGame> games = new LinkedHashMap<>();

    public GameManager(RiichiMahjongPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Lobby> lobbies() {
        return lobbies;
    }

    public Map<String, MahjongGame> games() {
        return games;
    }

    public Lobby lobbyOf(UUID playerId) {
        for (Lobby lobby : lobbies.values()) {
            if (lobby.players().contains(playerId)) {
                return lobby;
            }
        }
        return null;
    }

    public MahjongGame gameOf(UUID playerId) {
        for (MahjongGame game : games.values()) {
            if (game.seatOf(playerId) >= 0) {
                return game;
            }
        }
        return null;
    }

    public Lobby create(Player owner, Rules rules) {
        String id = nextId(owner.getName());
        Lobby lobby = new Lobby(id, owner.getUniqueId(), rules);
        lobbies.put(id, lobby);
        return lobby;
    }

    private String nextId(String base) {
        String candidate = base.toLowerCase();
        int suffix = 2;
        while (lobbies.containsKey(candidate) || games.containsKey(candidate)) {
            candidate = base.toLowerCase() + suffix++;
        }
        return candidate;
    }

    public boolean join(Lobby lobby, Player player) {
        if (lobby.players().size() >= 4 || lobby.players().contains(player.getUniqueId())) {
            return false;
        }
        lobby.players().add(player.getUniqueId());
        for (UUID id : lobby.players()) {
            Player other = Bukkit.getPlayer(id);
            if (other != null) {
                other.sendMessage(Component.text(player.getName() + " 加入了牌桌 " + lobby.id()
                        + " (" + lobby.players().size() + "/4)", NamedTextColor.GREEN));
            }
        }
        return true;
    }

    public void leave(Lobby lobby, Player player) {
        lobby.players().remove(player.getUniqueId());
        if (lobby.players().isEmpty()) {
            lobbies.remove(lobby.id());
        }
    }

    /** 開始對局；空位由電腦補齊。 */
    public MahjongGame start(Lobby lobby) {
        List<Player> players = new ArrayList<>();
        for (UUID id : lobby.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        lobbies.remove(lobby.id());
        MahjongGame game = new MahjongGame(plugin, lobby.id(), players, lobby.rules());
        games.put(lobby.id(), game);
        game.start();
        return game;
    }

    public void remove(MahjongGame game) {
        games.remove(game.id());
    }

    public void shutdown() {
        for (MahjongGame game : List.copyOf(games.values())) {
            game.stop("伺服器關閉，對局中止。");
        }
        games.clear();
        lobbies.clear();
    }
}
