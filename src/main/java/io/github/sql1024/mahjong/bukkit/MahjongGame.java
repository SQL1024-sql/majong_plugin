package io.github.sql1024.mahjong.bukkit;

import io.github.sql1024.mahjong.ai.SimpleBot;
import io.github.sql1024.mahjong.core.HandScore;
import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.Rules;
import io.github.sql1024.mahjong.core.Tile;
import io.github.sql1024.mahjong.core.YakuValue;
import io.github.sql1024.mahjong.game.CallOption;
import io.github.sql1024.mahjong.game.CallType;
import io.github.sql1024.mahjong.game.MahjongTable;
import io.github.sql1024.mahjong.game.RoundResult;
import io.github.sql1024.mahjong.game.Seat;
import io.github.sql1024.mahjong.game.TableListener;
import io.github.sql1024.mahjong.game.TurnOptions;
import io.github.sql1024.mahjong.game.WinEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** 把 {@link MahjongTable} 接到 Minecraft：負責畫面、聊天訊息、電腦代打與逾時。 */
public final class MahjongGame implements TableListener {

    private final RiichiMahjongPlugin plugin;
    private final String id;
    private final MahjongTable table;
    private final Map<Integer, UUID> humans = new LinkedHashMap<>();
    private final Map<UUID, TableGui> guis = new HashMap<>();

    private BukkitTask task;
    private long actionDeadline;
    private long botReadyAt;
    private long nextRoundAt;
    private boolean finished;

    public MahjongGame(RiichiMahjongPlugin plugin, String id, List<Player> players, Rules rules) {
        this.plugin = plugin;
        this.id = id;
        List<MahjongTable.SeatInfo> infos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i < players.size()) {
                Player player = players.get(i);
                infos.add(new MahjongTable.SeatInfo(player.getUniqueId(), player.getName(), false));
                humans.put(i, player.getUniqueId());
            } else {
                infos.add(new MahjongTable.SeatInfo(UUID.randomUUID(), "電腦" + (i - players.size() + 1), true));
            }
        }
        this.table = new MahjongTable(rules, infos, new Random());
        this.table.addListener(this);
    }

    public String id() {
        return id;
    }

    public MahjongTable table() {
        return table;
    }

    public boolean isFinished() {
        return finished;
    }

    public List<UUID> humanIds() {
        return List.copyOf(humans.values());
    }

    public int seatOf(UUID playerId) {
        for (Map.Entry<Integer, UUID> entry : humans.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public TableGui gui(UUID playerId) {
        return guis.get(playerId);
    }

    // ------------------------------------------------------------------
    // 生命週期
    // ------------------------------------------------------------------

    public void start() {
        for (Map.Entry<Integer, UUID> entry : humans.entrySet()) {
            TableGui gui = new TableGui(this, entry.getKey());
            guis.put(entry.getValue(), gui);
        }
        broadcast(Component.text("═══ 日本麻將開始！", NamedTextColor.GREEN));
        broadcast(Component.text("規則：" + describeRules(), NamedTextColor.GRAY));
        table.startRound();
        openAll();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private String describeRules() {
        Rules rules = table.rules();
        return (rules.targetRounds() == 1 ? "東風戰" : rules.targetRounds() == 2 ? "半莊戰" : "一莊戰")
                + "、赤寶牌 " + rules.akaCount() + " 張、"
                + (rules.kuitan() ? "有喰斷" : "無喰斷")
                + "、思考時間 " + rules.turnSeconds() + " 秒";
    }

    public void stop(String reason) {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        if (reason != null && !reason.isEmpty()) {
            broadcast(Component.text(reason, NamedTextColor.YELLOW));
        }
        for (UUID playerId : humans.values()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof TableGui) {
                player.closeInventory();
            }
        }
        plugin.manager().remove(this);
    }

    private void tick() {
        if (finished) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (table.phase()) {
            case GAME_ENDED -> stop("");
            case ROUND_ENDED -> {
                if (now >= nextRoundAt) {
                    table.startRound();
                    openAll();
                }
            }
            case TURN -> {
                Seat seat = table.seat(table.currentSeat());
                if (seat.isBot()) {
                    if (now >= botReadyAt) {
                        SimpleBot.actOnce(table);
                    }
                } else if (now >= actionDeadline) {
                    autoPlay(seat);
                }
            }
            case CALLS -> {
                for (int index : table.waitingSeats()) {
                    if (table.phase() != MahjongTable.Phase.CALLS) {
                        break;
                    }
                    Seat seat = table.seat(index);
                    if (seat.isBot() && now >= botReadyAt) {
                        SimpleBot.respond(table, seat, table.pendingOptions(index));
                    }
                }
                if (table.phase() == MahjongTable.Phase.CALLS && now >= actionDeadline) {
                    for (int index : table.waitingSeats()) {
                        table.declareCall(index, CallOption.of(CallType.PASS));
                    }
                }
            }
            default -> {
            }
        }
    }

    private void autoPlay(Seat seat) {
        Tile drawn = seat.drawn();
        if (drawn != null) {
            send(seat.index(), Component.text("時間到，自動摸切。", NamedTextColor.RED));
            table.discard(seat.index(), drawn, false);
        }
    }

    private void resetDeadline() {
        Rules rules = table.rules();
        actionDeadline = System.currentTimeMillis() + rules.turnSeconds() * 1000L;
        botReadyAt = System.currentTimeMillis() + plugin.botDelayMillis();
    }

    // ------------------------------------------------------------------
    // 玩家操作
    // ------------------------------------------------------------------

    /** GUI 點擊。 */
    public void handleClick(Player player, TableGui gui, int slot) {
        int seatIndex = gui.seatIndex();
        Seat seat = table.seat(seatIndex);
        Tile tile = gui.tileAt(slot);
        if (tile != null) {
            if (table.phase() != MahjongTable.Phase.TURN || table.currentSeat() != seatIndex) {
                player.sendMessage(Component.text("現在不是你的回合。", NamedTextColor.RED));
                return;
            }
            boolean riichi = gui.riichiArmed();
            if (!table.discard(seatIndex, tile, riichi)) {
                player.sendMessage(Component.text("這張牌現在不能打。", NamedTextColor.RED));
                return;
            }
            gui.setRiichiArmed(false);
            return;
        }

        switch (slot) {
            case TableGui.BUTTON_WIN -> {
                if (table.phase() == MahjongTable.Phase.TURN && table.currentSeat() == seatIndex) {
                    if (!table.declareTurnAction(seatIndex, CallOption.of(CallType.TSUMO))) {
                        player.sendMessage(Component.text("現在不能自摸。", NamedTextColor.RED));
                    }
                } else {
                    declareRon(seatIndex, player);
                }
            }
            case TableGui.BUTTON_RIICHI -> {
                if (table.phase() == MahjongTable.Phase.TURN && table.currentSeat() == seatIndex
                        && table.currentOptions().canRiichi()) {
                    gui.setRiichiArmed(!gui.riichiArmed());
                    player.sendMessage(gui.riichiArmed()
                            ? Component.text("立直模式：請點選要打出的牌。", NamedTextColor.GOLD)
                            : Component.text("已取消立直模式。", NamedTextColor.GRAY));
                    refresh(seatIndex);
                }
            }
            case TableGui.BUTTON_KAN -> {
                List<CallOption> kans = table.currentOptions().kanOptions();
                if (table.phase() == MahjongTable.Phase.TURN && table.currentSeat() == seatIndex
                        && !kans.isEmpty()) {
                    if (kans.size() == 1) {
                        table.declareTurnAction(seatIndex, kans.getFirst());
                    } else {
                        sendKanChoices(seatIndex, kans);
                    }
                }
            }
            case TableGui.BUTTON_HELP -> {
                if (table.phase() == MahjongTable.Phase.TURN && table.currentSeat() == seatIndex
                        && table.currentOptions().canKyuushu()) {
                    table.declareTurnAction(seatIndex, CallOption.of(CallType.KYUUSHU));
                } else {
                    player.sendMessage(Component.text("點手牌打出、聊天欄按鈕鳴牌，指令請看 /mj help",
                            NamedTextColor.GRAY));
                }
            }
            default -> {
            }
        }
    }

    private void declareRon(int seatIndex, Player player) {
        for (CallOption option : table.pendingOptions(seatIndex)) {
            if (option.type() == CallType.RON) {
                table.declareCall(seatIndex, option);
                return;
            }
        }
        player.sendMessage(Component.text("現在不能榮和。", NamedTextColor.RED));
    }

    private void sendKanChoices(int seatIndex, List<CallOption> kans) {
        Component message = Component.text("選擇要槓的牌：", NamedTextColor.YELLOW);
        for (int i = 0; i < kans.size(); i++) {
            CallOption option = kans.get(i);
            message = message.append(Component.text(" "))
                    .append(Component.text("[" + option.target().displayName() + "]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand("/mj kan " + option.target().shortName())));
        }
        send(seatIndex, message);
    }

    /** 由指令選擇待決定的鳴牌選項。 */
    public boolean chooseCall(int seatIndex, int optionIndex) {
        List<CallOption> options = table.pendingOptions(seatIndex);
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return false;
        }
        return table.declareCall(seatIndex, options.get(optionIndex));
    }

    public boolean pass(int seatIndex) {
        return table.declareCall(seatIndex, CallOption.of(CallType.PASS));
    }

    public boolean kan(int seatIndex, Tile tile) {
        for (CallOption option : table.currentOptions().kanOptions()) {
            if (option.target().id() == tile.id()) {
                return table.declareTurnAction(seatIndex, option);
            }
        }
        for (CallOption option : table.pendingOptions(seatIndex)) {
            if (option.type() == CallType.KAN) {
                return table.declareCall(seatIndex, option);
            }
        }
        return false;
    }

    public void openGui(Player player) {
        TableGui gui = guis.get(player.getUniqueId());
        if (gui == null) {
            return;
        }
        gui.refresh();
        player.openInventory(gui.getInventory());
    }

    /** 玩家離線 -> 由電腦代打。 */
    public void handleQuit(UUID playerId) {
        int seatIndex = seatOf(playerId);
        if (seatIndex < 0) {
            return;
        }
        Seat seat = table.seat(seatIndex);
        if (!seat.isBot()) {
            seat.setBot(true);
            broadcast(Component.text(seat.name() + " 離線了，改由電腦代打。", NamedTextColor.YELLOW));
        }
    }

    /** 玩家回到伺服器 -> 收回控制權。 */
    public void handleRejoin(Player player) {
        int seatIndex = seatOf(player.getUniqueId());
        if (seatIndex < 0) {
            return;
        }
        Seat seat = table.seat(seatIndex);
        seat.setBot(false);
        player.sendMessage(Component.text("歡迎回來，你的牌桌還在進行中。用 /mj open 打開畫面。",
                NamedTextColor.GREEN));
    }

    // ------------------------------------------------------------------
    // 事件 -> 畫面
    // ------------------------------------------------------------------

    @Override
    public void onRoundStart(MahjongTable table) {
        broadcast(Component.text("── " + table.roundName() + " ──", NamedTextColor.GREEN));
        broadcast(Component.text("寶牌指示牌：", NamedTextColor.GRAY)
                .append(TileRender.text(table.wall().doraIndicators())));
        refreshAll();
    }

    @Override
    public void onDraw(MahjongTable table, Seat seat, Tile tile, boolean replacement) {
        if (!seat.isBot()) {
            send(seat.index(), Component.text(replacement ? "嶺上摸牌：" : "摸牌：", NamedTextColor.GRAY)
                    .append(TileRender.text(tile)));
        }
        refreshAll();
    }

    @Override
    public void onTurnStart(MahjongTable table, Seat seat, TurnOptions options) {
        resetDeadline();
        refreshAll();
        if (seat.isBot()) {
            return;
        }
        List<Component> buttons = new ArrayList<>();
        if (options.canTsumo()) {
            buttons.add(button("自摸", NamedTextColor.GOLD, "/mj tsumo", "宣告自摸和牌"));
        }
        if (options.canRiichi()) {
            buttons.add(button("立直", NamedTextColor.YELLOW, "/mj riichi", "立直後只能摸切"));
        }
        for (CallOption option : options.kanOptions()) {
            buttons.add(button("槓 " + option.target().displayName(), NamedTextColor.AQUA,
                    "/mj kan " + option.target().shortName(), option.describe()));
        }
        if (options.canKyuushu()) {
            buttons.add(button("九種九牌", NamedTextColor.RED, "/mj kyuushu", "宣告途中流局"));
        }
        Component line = Component.text("輪到你了 (" + table.rules().turnSeconds() + " 秒)：",
                NamedTextColor.GREEN);
        for (Component component : buttons) {
            line = line.append(Component.text(" ")).append(component);
        }
        send(seat.index(), line);
        Player player = playerAt(seat.index());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
        }
    }

    @Override
    public void onDiscard(MahjongTable table, Seat seat, Tile tile, boolean riichiDeclaration) {
        if (plugin.logDiscards()) {
            broadcast(Component.text(seat.wind().displayName() + " " + seat.name() + " 打出 ",
                            NamedTextColor.GRAY)
                    .append(TileRender.text(tile))
                    .append(riichiDeclaration
                            ? Component.text(" (立直宣言)", NamedTextColor.GOLD) : Component.empty()));
        }
        refreshAll();
    }

    @Override
    public void onRiichi(MahjongTable table, Seat seat) {
        broadcast(Component.text("★ " + seat.name() + " 宣告立直！", NamedTextColor.GOLD));
        playSoundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
    }

    @Override
    public void onCallOptions(MahjongTable table, Seat seat, List<CallOption> options, Tile target, Seat from) {
        resetDeadline();
        refreshAll();
        if (seat.isBot()) {
            return;
        }
        Component line = Component.text(from.name() + " 打出 ", NamedTextColor.YELLOW)
                .append(TileRender.text(target))
                .append(Component.text(" → ", NamedTextColor.YELLOW));
        for (int i = 0; i < options.size(); i++) {
            CallOption option = options.get(i);
            NamedTextColor color = option.type() == CallType.RON ? NamedTextColor.GOLD : NamedTextColor.AQUA;
            line = line.append(Component.text(" "))
                    .append(button(option.describe(), color, "/mj call " + i, option.describe()));
        }
        line = line.append(Component.text(" "))
                .append(button("跳過", NamedTextColor.GRAY, "/mj pass", "不鳴牌"));
        send(seat.index(), line);
        Player player = playerAt(seat.index());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);
        }
    }

    @Override
    public void onCall(MahjongTable table, Seat seat, Meld meld) {
        broadcast(Component.text(seat.name() + " 宣告 " + meld.type().displayName() + " ",
                        NamedTextColor.LIGHT_PURPLE)
                .append(TileRender.text(meld.tiles())));
        refreshAll();
    }

    @Override
    public void onNewDora(MahjongTable table, Tile indicator) {
        broadcast(Component.text("翻開新寶牌指示牌：", NamedTextColor.GOLD).append(TileRender.text(indicator)));
        refreshAll();
    }

    @Override
    public void onRoundEnd(MahjongTable table, RoundResult result) {
        nextRoundAt = System.currentTimeMillis() + plugin.roundBreakMillis();
        broadcast(Component.text("──────────────", NamedTextColor.DARK_GRAY));
        switch (result.type()) {
            case TSUMO, RON -> {
                for (WinEntry entry : result.wins()) {
                    Seat winner = table.seat(entry.seat());
                    Component header = Component.text(
                            winner.name() + (entry.isTsumo() ? " 自摸！" : " 榮和 "
                                    + table.seat(entry.fromSeat()).name() + " 的 "), NamedTextColor.GOLD);
                    if (!entry.isTsumo()) {
                        header = header.append(TileRender.text(entry.winTile()));
                    }
                    broadcast(header);
                    broadcast(Component.text("  手牌：", NamedTextColor.GRAY)
                            .append(TileRender.text(entry.concealed())));
                    for (Meld meld : entry.melds()) {
                        broadcast(Component.text("  ").append(TileRender.text(meld)));
                    }
                    broadcast(scoreComponent(entry.score()));
                    broadcast(Component.text("  得點：+" + entry.gain(), NamedTextColor.GREEN));
                }
                playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 1f);
            }
            case EXHAUSTIVE_DRAW -> {
                StringBuilder tenpai = new StringBuilder();
                for (int index : result.tenpaiSeats()) {
                    tenpai.append(table.seat(index).name()).append(' ');
                }
                broadcast(Component.text("荒牌流局 — 聽牌：" + (tenpai.isEmpty() ? "無" : tenpai.toString().trim()),
                        NamedTextColor.YELLOW));
            }
            case ABORTIVE_DRAW -> broadcast(Component.text("途中流局 — " + result.reason(),
                    NamedTextColor.YELLOW));
        }
        int[] deltas = result.deltas();
        for (Seat seat : table.seats()) {
            int delta = deltas[seat.index()];
            NamedTextColor color = delta > 0 ? NamedTextColor.GREEN
                    : delta < 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
            broadcast(Component.text("  " + seat.wind().displayName() + " " + seat.name() + "："
                    + seat.points() + " 點 (" + (delta >= 0 ? "+" : "") + delta + ")", color));
        }
        refreshAll();
    }

    private Component scoreComponent(HandScore score) {
        Component line = Component.text("  ", NamedTextColor.GRAY);
        List<String> parts = new ArrayList<>();
        for (YakuValue value : score.yaku()) {
            parts.add(value.toString());
        }
        return line.append(Component.text(String.join("、", parts), NamedTextColor.AQUA))
                .append(Component.text(" — " + score.describeHan(), NamedTextColor.GOLD));
    }

    @Override
    public void onGameEnd(MahjongTable table) {
        showStandings();
    }

    private void showStandings() {
        broadcast(Component.text("═══ 對局結束 ═══", NamedTextColor.GREEN));
        int rank = 1;
        for (Seat seat : table.standings()) {
            broadcast(Component.text("  第 " + rank + " 名：" + seat.name() + " — " + seat.points() + " 點",
                    rank == 1 ? NamedTextColor.GOLD : NamedTextColor.WHITE));
            rank++;
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private Component button(String text, NamedTextColor color, String command, String hover) {
        return Component.text("[" + text + "]", color)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private Player playerAt(int seatIndex) {
        UUID playerId = humans.get(seatIndex);
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    public void broadcast(Component message) {
        for (UUID playerId : humans.values()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void send(int seatIndex, Component message) {
        Player player = playerAt(seatIndex);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    private void playSoundAll(Sound sound, float pitch) {
        for (UUID playerId : humans.values()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), sound, 0.8f, pitch);
            }
        }
    }

    private void refresh(int seatIndex) {
        UUID playerId = humans.get(seatIndex);
        if (playerId == null) {
            return;
        }
        TableGui gui = guis.get(playerId);
        if (gui != null) {
            gui.refresh();
        }
    }

    private void refreshAll() {
        for (TableGui gui : guis.values()) {
            gui.refresh();
        }
    }

    private void openAll() {
        for (Map.Entry<Integer, UUID> entry : humans.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getValue());
            TableGui gui = guis.get(entry.getValue());
            if (player == null || gui == null) {
                continue;
            }
            gui.refresh();
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof TableGui)) {
                player.openInventory(gui.getInventory());
            }
        }
    }
}
