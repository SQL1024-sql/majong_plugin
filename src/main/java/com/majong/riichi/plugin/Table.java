package com.majong.riichi.plugin;

import com.majong.riichi.bot.SimpleBot;
import com.majong.riichi.core.Meld;
import com.majong.riichi.core.Tile;
import com.majong.riichi.game.Action;
import com.majong.riichi.game.GameListener;
import com.majong.riichi.game.GameRules;
import com.majong.riichi.game.HandResult;
import com.majong.riichi.game.RiichiGame;
import com.majong.riichi.game.Variant;
import com.majong.riichi.plugin.scene.TableScene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A table in the world: the four seats, the game being played on them and the
 * chat traffic that keeps everybody informed.
 */
public final class Table implements GameListener {

    /** Where a table is in its life cycle. */
    public enum State {
        LOBBY,
        PLAYING,
        FINISHED
    }

    private static final String[] BOT_NAMES = {"東家CPU", "南家CPU", "西家CPU", "北家CPU"};

    private final MahjongPlugin plugin;
    private final String name;
    private final Variant variant;
    private final UUID owner;
    private final UUID[] players = new UUID[RiichiGame.SEATS];
    private final boolean[] bots = new boolean[RiichiGame.SEATS];

    private RiichiGame game;
    private State state = State.LOBBY;
    private final TableScene scene;

    private final Set<Integer> prompted = new HashSet<>();
    private final Map<Integer, BukkitTask> timeouts = new HashMap<>();
    private BukkitTask scheduled;

    Table(MahjongPlugin plugin, String name, Variant variant, Player owner) {
        this.plugin = plugin;
        this.name = name;
        this.variant = variant;
        this.owner = owner.getUniqueId();
        players[0] = owner.getUniqueId();
        this.scene = plugin.createScene();
    }

    /** The tiles standing in the world for this table, if it has been placed. */
    public TableScene scene() {
        return scene;
    }

    /** Puts the table down in front of the given player and redraws it. */
    public void placeAt(Location viewer) {
        scene.placeFor(viewer);
        refreshScene();
    }

    private void refreshScene() {
        if (state == State.PLAYING && game != null && scene.isPlaced()) {
            scene.refresh(game, players.clone());
        }
    }

    // ----------------------------------------------------------------- seats

    public String name() {
        return name;
    }

    /** Which game this table deals. */
    public Variant variant() {
        return variant;
    }

    public State state() {
        return state;
    }

    public UUID owner() {
        return owner;
    }

    public RiichiGame game() {
        return game;
    }

    public boolean isHuman(int seat) {
        return players[seat] != null;
    }

    public String displayName(int seat) {
        if (players[seat] != null) {
            Player player = Bukkit.getPlayer(players[seat]);
            if (player != null) {
                return player.getName();
            }
            return "(離席)";
        }
        return bots[seat] ? BOT_NAMES[seat] : "空席";
    }

    public int seatOf(UUID uuid) {
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            if (uuid.equals(players[seat])) {
                return seat;
            }
        }
        return -1;
    }

    public int freeSeats() {
        int free = 0;
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            if (players[seat] == null && !bots[seat]) {
                free++;
            }
        }
        return free;
    }

    /** Seats a player; returns their seat, or {@code -1} when the table is full. */
    public int addPlayer(Player player) {
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            if (players[seat] == null && !bots[seat]) {
                players[seat] = player.getUniqueId();
                return seat;
            }
        }
        return -1;
    }

    /** Fills one empty seat with a computer player. */
    public int addBot() {
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            if (players[seat] == null && !bots[seat]) {
                bots[seat] = true;
                return seat;
            }
        }
        return -1;
    }

    /** Fills every empty seat with computer players. */
    public int fillWithBots() {
        int added = 0;
        while (addBot() >= 0) {
            added++;
        }
        return added;
    }

    public boolean remove(UUID uuid) {
        int seat = seatOf(uuid);
        if (seat < 0) {
            return false;
        }
        players[seat] = null;
        if (state == State.PLAYING) {
            // Hand the empty seat to a bot so the game can carry on.
            bots[seat] = true;
            broadcast(Component.text(displayName(seat) + " 離開了座位", NamedTextColor.GRAY));
            advance();
        }
        return true;
    }

    public List<UUID> humanPlayers() {
        List<UUID> humans = new ArrayList<>();
        for (UUID uuid : players) {
            if (uuid != null) {
                humans.add(uuid);
            }
        }
        return humans;
    }

    // ------------------------------------------------------------ game flow

    /** Starts the game once every seat is taken. */
    public boolean start(GameRules rules) {
        if (state != State.LOBBY || freeSeats() > 0) {
            return false;
        }
        List<String> names = new ArrayList<>(RiichiGame.SEATS);
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            names.add(displayName(seat));
        }
        game = new RiichiGame(rules, names, System.nanoTime(), this);
        game.taiwanRules(plugin.taiwanStakes());
        state = State.PLAYING;
        if (!scene.isPlaced()) {
            Player host = Bukkit.getPlayer(owner);
            if (host != null) {
                scene.placeFor(host.getLocation());
            }
        }
        game.startHand();
        advance();
        return true;
    }

    /** Stops the table, cancelling anything still scheduled. */
    public void shutdown() {
        cancelScheduled();
        for (BukkitTask task : timeouts.values()) {
            task.cancel();
        }
        timeouts.clear();
        prompted.clear();
        scene.clear();
        state = State.FINISHED;
    }

    /**
     * Applies a player's decision. Returns {@code false} when it is not legal,
     * which happens when a click arrives after the moment has passed.
     */
    public boolean submit(int seat, Action action) {
        if (state != State.PLAYING || !game.options(seat).contains(action)) {
            return false;
        }
        clearPrompt(seat);
        game.act(seat, action);
        advance();
        return true;
    }

    /** Works out what to do next: prompt a player, move a bot, or deal again. */
    private void advance() {
        if (state != State.PLAYING) {
            return;
        }
        refreshScene();
        if (game.isGameOver()) {
            broadcast(TableView.standings(game, this));
            shutdown();
            return;
        }
        if (game.phase() == RiichiGame.Phase.IDLE) {
            scheduleNextHand();
            return;
        }
        List<Integer> waiting = waitingSeats();
        for (int seat : new ArrayList<>(prompted)) {
            if (!waiting.contains(seat)) {
                clearPrompt(seat);
            }
        }
        for (int seat : waiting) {
            if (isHuman(seat) && prompted.add(seat)) {
                promptSeat(seat);
                startTimeout(seat);
            }
        }
        for (int seat : waiting) {
            if (!isHuman(seat)) {
                scheduleBotMove(seat);
                return;
            }
        }
    }

    private List<Integer> waitingSeats() {
        List<Integer> waiting = new ArrayList<>(RiichiGame.SEATS);
        if (game.phase() == RiichiGame.Phase.ACT) {
            waiting.add(game.currentSeat());
            return waiting;
        }
        if (game.phase() == RiichiGame.Phase.CALL) {
            for (int seat : game.pendingSeats()) {
                if (!game.options(seat).isEmpty()) {
                    waiting.add(seat);
                }
            }
        }
        return waiting;
    }

    private void scheduleBotMove(int seat) {
        cancelScheduled();
        scheduled = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduled = null;
            if (state != State.PLAYING) {
                return;
            }
            Action action = SimpleBot.decide(game, seat);
            if (action != null) {
                game.act(seat, action);
            }
            advance();
        }, plugin.botDelayTicks());
    }

    private void scheduleNextHand() {
        cancelScheduled();
        scheduled = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scheduled = null;
            if (state != State.PLAYING || game.isGameOver()) {
                return;
            }
            game.startHand();
            advance();
        }, plugin.nextHandDelayTicks());
    }

    private void cancelScheduled() {
        if (scheduled != null) {
            scheduled.cancel();
            scheduled = null;
        }
    }

    // ------------------------------------------------------------- prompting

    private void promptSeat(int seat) {
        Player player = playerAt(seat);
        if (player == null) {
            return;
        }
        TileRenderer renderer = plugin.rendererFor(player);
        List<Action> options = game.options(seat);
        if (renderer.isGraphical()) {
            // Drawn tiles are taller than a chat line, so give them room.
            player.sendMessage(Component.empty());
        }
        player.sendMessage(TableView.header(renderer, game));
        player.sendMessage(TableView.hand(renderer, game, seat, options));
        Component prompt = TableView.prompt(options);
        if (!Component.empty().equals(prompt)) {
            player.sendMessage(prompt);
        }
    }

    private void startTimeout(int seat) {
        cancelTimeout(seat);
        timeouts.put(seat, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            timeouts.remove(seat);
            if (state != State.PLAYING || !prompted.contains(seat)) {
                return;
            }
            Action fallback = defaultAction(seat);
            prompted.remove(seat);
            if (fallback != null) {
                game.act(seat, fallback);
            }
            advance();
        }, plugin.turnTimeoutTicks()));
    }

    /** What happens when somebody runs out of time: throw the drawn tile, or pass. */
    private Action defaultAction(int seat) {
        List<Action> options = game.options(seat);
        Tile drawn = game.seat(seat).hand().drawn();
        Action firstDiscard = null;
        for (Action action : options) {
            if (action instanceof Action.Pass) {
                return action;
            }
            if (action instanceof Action.Discard discard && !discard.riichi()) {
                if (discard.tile().equals(drawn)) {
                    return action;
                }
                if (firstDiscard == null) {
                    firstDiscard = action;
                }
            }
        }
        return firstDiscard;
    }

    private void clearPrompt(int seat) {
        prompted.remove(seat);
        cancelTimeout(seat);
    }

    private void cancelTimeout(int seat) {
        BukkitTask task = timeouts.remove(seat);
        if (task != null) {
            task.cancel();
        }
    }

    private Player playerAt(int seat) {
        return players[seat] == null ? null : Bukkit.getPlayer(players[seat]);
    }

    public void broadcast(Component message) {
        broadcast(renderer -> message);
    }

    /**
     * Sends a message built per recipient, since players at one table may be
     * seeing drawn tiles or plain text depending on their resource pack.
     */
    public void broadcast(Function<TileRenderer, Component> message) {
        for (UUID uuid : players) {
            if (uuid == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message.apply(plugin.rendererFor(player)));
            }
        }
    }

    // -------------------------------------------------------- game listener

    @Override
    public void onHandStarted(RiichiGame game) {
        broadcast(TableView.separator());
        broadcast(renderer -> TableView.header(renderer, game));
        broadcast(renderer -> TableView.seats(renderer, game, this));
    }

    @Override
    public void onDiscard(RiichiGame game, int seat, Tile tile, boolean riichi) {
        broadcast(renderer -> Component.text()
                .append(Component.text(displayName(seat) + " 打 ", NamedTextColor.GRAY))
                .append(renderer.render(tile))
                .append(riichi ? Component.text("  立直!", NamedTextColor.RED) : Component.empty())
                .build());
    }

    @Override
    public void onCall(RiichiGame game, int seat, Meld meld) {
        broadcast(renderer -> Component.text()
                .append(Component.text(displayName(seat) + " " + callName(meld), NamedTextColor.AQUA))
                .append(TableView.meldComponent(renderer, meld))
                .build());
    }

    private static String callName(Meld meld) {
        return switch (meld.type()) {
            case CHI -> "吃";
            case PON -> "碰";
            case ANKAN -> "暗槓";
            case DAIMINKAN -> "明槓";
            case SHOUMINKAN -> "加槓";
        };
    }

    @Override
    public void onFlower(RiichiGame game, int seat, com.majong.riichi.core.Flower flower) {
        broadcast(Component.text(displayName(seat) + " 補花 " + flower.display(),
                NamedTextColor.LIGHT_PURPLE));
    }

    @Override
    public void onDoraRevealed(RiichiGame game, Tile indicator) {
        broadcast(renderer -> Component.text()
                .append(Component.text("新寶牌指示 ", NamedTextColor.GOLD))
                .append(renderer.render(indicator))
                .build());
    }

    @Override
    public void onHandFinished(RiichiGame game, HandResult result) {
        broadcast(renderer -> TableView.result(renderer, game, this, result));
    }
}
