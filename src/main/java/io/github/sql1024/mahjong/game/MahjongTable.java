package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.HandParser;
import io.github.sql1024.mahjong.core.HandScore;
import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.MeldType;
import io.github.sql1024.mahjong.core.Rules;
import io.github.sql1024.mahjong.core.ScoreCalculator;
import io.github.sql1024.mahjong.core.ShantenCalculator;
import io.github.sql1024.mahjong.core.Tile;
import io.github.sql1024.mahjong.core.Wall;
import io.github.sql1024.mahjong.core.Wind;
import io.github.sql1024.mahjong.core.WinContext;
import io.github.sql1024.mahjong.core.YakuEvaluator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 一張日本麻將牌桌：負責整局的流程、規則判定與計分。
 *
 * <p>這個類別完全不依賴 Minecraft，所有互動都透過 {@link TableListener} 通知，
 * 並由外部呼叫 {@link #discard} / {@link #declareTurnAction} / {@link #declareCall} 推進。
 */
public final class MahjongTable {

    /** 目前的階段。 */
    public enum Phase {
        /** 尚未開始 */
        IDLE,
        /** 等待某人打牌 */
        TURN,
        /** 等待其他人決定要不要鳴牌 */
        CALLS,
        /** 一局已結束，等待開始下一局 */
        ROUND_ENDED,
        /** 對局結束 */
        GAME_ENDED
    }

    private final Rules rules;
    private final Random random;
    private final Seat[] seats;
    private final List<TableListener> listeners = new ArrayList<>();

    private Wall wall;
    private Wind roundWind = Wind.EAST;
    private int roundNumber = 1;
    private int honba;
    private int riichiSticks;
    private int dealerSeat;
    private int currentSeat;
    private Phase phase = Phase.IDLE;

    private Tile lastDiscard;
    private int lastDiscardSeat = -1;
    private final Map<Integer, List<CallOption>> pendingCalls = new LinkedHashMap<>();
    private final Map<Integer, CallOption> decisions = new HashMap<>();

    private boolean awaitingChankan;
    private int chankanSeat = -1;
    private Tile chankanTile;
    private MeldType chankanMeldType;

    private boolean rinshanDraw;
    private boolean anyCallMade;
    private int totalKans;
    private TurnOptions currentOptions = new TurnOptions(false, List.of(), List.of(), false);
    private RoundResult lastResult;
    /** 這一局中因為立直而先付出的點數，結算時要一起呈現。 */
    private final int[] roundDeltas = new int[4];

    public MahjongTable(Rules rules, List<SeatInfo> players, Random random) {
        if (players.size() != 4) {
            throw new IllegalArgumentException("A mahjong table needs exactly 4 players");
        }
        this.rules = rules;
        this.random = random;
        this.seats = new Seat[4];
        for (int i = 0; i < 4; i++) {
            SeatInfo info = players.get(i);
            seats[i] = new Seat(i, info.id(), info.name(), info.bot(), rules.startingPoints());
        }
    }

    /** 建立牌桌時的玩家資料。 */
    public record SeatInfo(java.util.UUID id, String name, boolean bot) {
    }

    // ------------------------------------------------------------------
    // 基本存取
    // ------------------------------------------------------------------

    public void addListener(TableListener listener) {
        listeners.add(listener);
    }

    public Rules rules() {
        return rules;
    }

    public Seat seat(int index) {
        return seats[index];
    }

    public List<Seat> seats() {
        return List.of(seats);
    }

    public Wall wall() {
        return wall;
    }

    public Wind roundWind() {
        return roundWind;
    }

    public int roundNumber() {
        return roundNumber;
    }

    public int honba() {
        return honba;
    }

    public int riichiSticks() {
        return riichiSticks;
    }

    public int dealerSeat() {
        return dealerSeat;
    }

    public int currentSeat() {
        return currentSeat;
    }

    public Phase phase() {
        return phase;
    }

    public TurnOptions currentOptions() {
        return currentOptions;
    }

    public RoundResult lastResult() {
        return lastResult;
    }

    public Tile lastDiscard() {
        return lastDiscard;
    }

    public int lastDiscardSeat() {
        return lastDiscardSeat;
    }

    /** 還在等待哪些人做鳴牌決定。 */
    public List<Integer> waitingSeats() {
        List<Integer> waiting = new ArrayList<>();
        for (Map.Entry<Integer, List<CallOption>> entry : pendingCalls.entrySet()) {
            if (!decisions.containsKey(entry.getKey())) {
                waiting.add(entry.getKey());
            }
        }
        return waiting;
    }

    public List<CallOption> pendingOptions(int seat) {
        return pendingCalls.getOrDefault(seat, List.of());
    }

    /** 這一局的局名，例如「東二局 1 本場」。 */
    public String roundName() {
        return roundWind.displayName() + roundNumber + "局 " + honba + "本場";
    }

    // ------------------------------------------------------------------
    // 開局
    // ------------------------------------------------------------------

    /** 開始新的一局。 */
    public void startRound() {
        if (phase == Phase.GAME_ENDED) {
            return;
        }
        wall = new Wall(random, rules.akaCount());
        java.util.Arrays.fill(roundDeltas, 0);
        anyCallMade = false;
        totalKans = 0;
        rinshanDraw = false;
        awaitingChankan = false;
        lastDiscard = null;
        lastDiscardSeat = -1;
        pendingCalls.clear();
        decisions.clear();

        for (int i = 0; i < 4; i++) {
            Seat seat = seats[i];
            seat.resetForRound(Wind.values()[Math.floorMod(i - dealerSeat, 4)]);
        }
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                Seat seat = seats[(dealerSeat + i) % 4];
                for (int n = 0; n < 4; n++) {
                    seat.addTile(wall.draw());
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            Seat seat = seats[(dealerSeat + i) % 4];
            seat.addTile(wall.draw());
            seat.refreshWaits();
        }

        currentSeat = dealerSeat;
        phase = Phase.TURN;
        for (TableListener listener : listeners) {
            listener.onRoundStart(this);
        }
        drawAndStartTurn(false);
    }

    private void drawAndStartTurn(boolean replacement) {
        Seat seat = seats[currentSeat];
        Tile tile;
        if (replacement) {
            tile = wall.drawReplacement();
        } else {
            tile = wall.draw();
            if (tile == null) {
                exhaustiveDraw();
                return;
            }
        }
        rinshanDraw = replacement;
        seat.setDrawn(tile);
        for (TableListener listener : listeners) {
            listener.onDraw(this, seat, tile, replacement);
        }
        startTurn();
    }

    private void startTurn() {
        Seat seat = seats[currentSeat];
        seat.setTemporaryFuriten(false);
        currentOptions = computeTurnOptions(seat);
        phase = Phase.TURN;
        for (TableListener listener : listeners) {
            listener.onTurnStart(this, seat, currentOptions);
        }
    }

    // ------------------------------------------------------------------
    // 自己的回合
    // ------------------------------------------------------------------

    private TurnOptions computeTurnOptions(Seat seat) {
        Tile drawn = seat.drawn();
        boolean canTsumo = false;
        boolean canKyuushu = false;
        List<Tile> riichiDiscards = new ArrayList<>();
        List<CallOption> kanOptions = new ArrayList<>();

        if (drawn != null) {
            HandScore score = YakuEvaluator.evaluate(buildWinContext(seat, drawn, true));
            canTsumo = score != null && score.hasYaku();

            if (seat.firstUninterruptedTurn() && seat.discards().isEmpty() && rules.abortiveDraws()) {
                canKyuushu = countTerminalKinds(seat.fullHand()) >= 9;
            }
            if (wall.canKan() && wall.remaining() > 0) {
                kanOptions.addAll(computeSelfKanOptions(seat));
            }
            if (!seat.riichi() && seat.isClosed() && seat.points() >= 1000 && wall.remaining() >= 4) {
                riichiDiscards.addAll(computeRiichiDiscards(seat));
            }
        }
        return new TurnOptions(canTsumo, riichiDiscards, kanOptions, canKyuushu);
    }

    private static int countTerminalKinds(List<Tile> tiles) {
        boolean[] seen = new boolean[Tile.KINDS];
        int count = 0;
        for (Tile tile : tiles) {
            if (tile.isTerminalOrHonor() && !seen[tile.id()]) {
                seen[tile.id()] = true;
                count++;
            }
        }
        return count;
    }

    private List<CallOption> computeSelfKanOptions(Seat seat) {
        List<CallOption> options = new ArrayList<>();
        List<Tile> full = seat.fullHand();
        int[] counts = Tile.toCounts(full);

        for (int id = 0; id < Tile.KINDS; id++) {
            if (counts[id] < 4) {
                continue;
            }
            if (seat.riichi() && !isRiichiAnkanAllowed(seat, id)) {
                continue;
            }
            List<Tile> tiles = new ArrayList<>();
            for (Tile tile : full) {
                if (tile.id() == id) {
                    tiles.add(tile);
                }
            }
            options.add(new CallOption(CallType.KAN, tiles.subList(0, 4), Tile.of(id)));
        }

        if (!seat.riichi()) {
            for (Meld meld : seat.melds()) {
                if (meld.type() != MeldType.PON) {
                    continue;
                }
                int id = meld.baseTileId();
                for (Tile tile : full) {
                    if (tile.id() == id) {
                        options.add(new CallOption(CallType.KAN, List.of(tile), Tile.of(id)));
                        break;
                    }
                }
            }
        }
        return options;
    }

    /** 立直中只能暗槓摸到的那張牌，而且不能改變聽牌型。 */
    private boolean isRiichiAnkanAllowed(Seat seat, int tileId) {
        Tile drawn = seat.drawn();
        if (drawn == null || drawn.id() != tileId) {
            return false;
        }
        int[] before = Tile.toCounts(seat.hand());
        List<Integer> waitsBefore = ShantenCalculator.waits(before, seat.melds());

        int[] after = before.clone();
        after[tileId] -= 3;
        List<Meld> melds = new ArrayList<>(seat.melds());
        melds.add(Meld.ankan(List.of(Tile.of(tileId), Tile.of(tileId), Tile.of(tileId), Tile.of(tileId))));
        List<Integer> waitsAfter = ShantenCalculator.waits(after, melds);
        return waitsBefore.equals(waitsAfter);
    }

    private List<Tile> computeRiichiDiscards(Seat seat) {
        List<Tile> result = new ArrayList<>();
        List<Tile> full = seat.fullHand();
        boolean[] tried = new boolean[Tile.KINDS];
        for (Tile candidate : full) {
            if (tried[candidate.id()]) {
                continue;
            }
            tried[candidate.id()] = true;
            List<Tile> remaining = new ArrayList<>(full);
            remaining.remove(candidate);
            int[] counts = Tile.toCounts(remaining);
            if (!ShantenCalculator.waits(counts, seat.melds()).isEmpty()) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 宣告自摸 / 暗槓 / 加槓 / 九種九牌。
     *
     * @return 是否成功
     */
    public boolean declareTurnAction(int seatIndex, CallOption option) {
        if (phase != Phase.TURN || seatIndex != currentSeat) {
            return false;
        }
        Seat seat = seats[seatIndex];
        return switch (option.type()) {
            case TSUMO -> {
                if (!currentOptions.canTsumo()) {
                    yield false;
                }
                declareTsumo(seat);
                yield true;
            }
            case KAN -> executeSelfKan(seat, option);
            case KYUUSHU -> {
                if (!currentOptions.canKyuushu()) {
                    yield false;
                }
                abortiveDraw("九種九牌");
                yield true;
            }
            default -> false;
        };
    }

    private void declareTsumo(Seat seat) {
        Tile winTile = seat.drawn();
        WinContext ctx = buildWinContext(seat, winTile, true);
        HandScore score = YakuEvaluator.evaluate(ctx);
        if (score == null || !score.hasYaku()) {
            return;
        }
        List<Tile> concealed = new ArrayList<>(seat.hand());
        concealed.add(winTile);
        finishWithWins(List.of(new PendingWin(seat.index(), -1, winTile, concealed, score)));
    }

    private boolean executeSelfKan(Seat seat, CallOption option) {
        boolean allowed = currentOptions.kanOptions().stream()
                .anyMatch(candidate -> candidate.target().id() == option.target().id()
                        && candidate.handTiles().size() == option.handTiles().size());
        if (!allowed || !wall.canKan()) {
            return false;
        }
        int tileId = option.target().id();
        Meld existingPon = null;
        for (Meld meld : seat.melds()) {
            if (meld.type() == MeldType.PON && meld.baseTileId() == tileId) {
                existingPon = meld;
                break;
            }
        }

        seat.mergeDrawn();
        if (existingPon != null && option.handTiles().size() == 1) {
            Tile added = takeFromHand(seat, tileId, 1).getFirst();
            Meld kakan = existingPon.toKakan(added);
            seat.melds().set(seat.melds().indexOf(existingPon), kakan);
            seat.refreshWaits();
            totalKans++;
            notifyCall(seat, kakan);
            if (startChankanCheck(seat, added, MeldType.KAKAN)) {
                return true;
            }
            wall.revealNewDora();
            notifyNewDora();
            afterKan(seat);
            return true;
        }

        List<Tile> tiles = takeFromHand(seat, tileId, 4);
        Meld ankan = Meld.ankan(tiles);
        seat.addMeld(ankan);
        seat.refreshWaits();
        totalKans++;
        notifyCall(seat, ankan);
        // 國士無雙可以搶暗槓 (依規則設定)
        if (startChankanCheck(seat, Tile.of(tileId), MeldType.ANKAN)) {
            return true;
        }
        wall.revealNewDora();
        notifyNewDora();
        afterKan(seat);
        return true;
    }

    private void afterKan(Seat seat) {
        clearIppatsuAll();
        if (totalKans >= 4 && rules.abortiveDraws() && !isFourKansBySamePlayer()) {
            abortiveDraw("四槓散了");
            return;
        }
        currentSeat = seat.index();
        drawAndStartTurn(true);
    }

    private boolean isFourKansBySamePlayer() {
        for (Seat seat : seats) {
            int kans = 0;
            for (Meld meld : seat.melds()) {
                if (meld.isKan()) {
                    kans++;
                }
            }
            if (kans == 4) {
                return true;
            }
        }
        return false;
    }

    private List<Tile> takeFromHand(Seat seat, int tileId, int count) {
        List<Tile> taken = new ArrayList<>();
        List<Tile> hand = seat.hand();
        for (int i = hand.size() - 1; i >= 0 && taken.size() < count; i--) {
            if (hand.get(i).id() == tileId) {
                taken.add(hand.remove(i));
            }
        }
        return taken;
    }

    // ------------------------------------------------------------------
    // 打牌
    // ------------------------------------------------------------------

    /**
     * 打出一張牌。
     *
     * @param declareRiichi 是否同時宣告立直
     * @return 是否成功
     */
    public boolean discard(int seatIndex, Tile tile, boolean declareRiichi) {
        if (phase != Phase.TURN || seatIndex != currentSeat) {
            return false;
        }
        Seat seat = seats[seatIndex];
        if (declareRiichi && !currentOptions.riichiDiscards().contains(tile)) {
            boolean sameKind = currentOptions.riichiDiscards().stream()
                    .anyMatch(candidate -> candidate.id() == tile.id());
            if (!sameKind) {
                return false;
            }
        }
        if (seat.riichi() && !declareRiichi) {
            // 立直後只能摸切 (暗槓另外處理)
            if (seat.drawn() != null && !tile.equals(seat.drawn())) {
                return false;
            }
        }

        boolean tsumogiri = seat.drawn() != null && tile.equals(seat.drawn());
        if (!seat.removeTile(tile)) {
            return false;
        }
        seat.mergeDrawn();

        boolean firstTurn = seat.firstUninterruptedTurn();
        if (seat.riichi() && !declareRiichi) {
            seat.clearIppatsu();
        }
        if (declareRiichi) {
            seat.declareRiichi(firstTurn && seat.discards().isEmpty());
            seat.addPoints(-1000);
            roundDeltas[seatIndex] -= 1000;
            riichiSticks++;
            for (TableListener listener : listeners) {
                listener.onRiichi(this, seat);
            }
        }

        Discard discard = new Discard(tile, declareRiichi, tsumogiri);
        seat.discards().add(discard);
        seat.refreshWaits();
        lastDiscard = tile;
        lastDiscardSeat = seatIndex;

        for (TableListener listener : listeners) {
            listener.onDiscard(this, seat, tile, declareRiichi);
        }

        if (checkFourWinds()) {
            return true;
        }
        if (checkFourRiichi()) {
            return true;
        }
        collectCalls(seat, tile);
        return true;
    }

    private boolean checkFourWinds() {
        if (!rules.abortiveDraws()) {
            return false;
        }
        int wind = -1;
        for (Seat seat : seats) {
            if (!seat.melds().isEmpty() || seat.discards().size() != 1) {
                return false;
            }
            Tile tile = seat.discards().getFirst().tile();
            if (!tile.isWind()) {
                return false;
            }
            if (wind == -1) {
                wind = tile.id();
            } else if (wind != tile.id()) {
                return false;
            }
        }
        abortiveDraw("四風連打");
        return true;
    }

    private boolean checkFourRiichi() {
        if (!rules.abortiveDraws()) {
            return false;
        }
        for (Seat seat : seats) {
            if (!seat.riichi()) {
                return false;
            }
        }
        abortiveDraw("四家立直");
        return true;
    }

    // ------------------------------------------------------------------
    // 鳴牌
    // ------------------------------------------------------------------

    private void collectCalls(Seat from, Tile tile) {
        pendingCalls.clear();
        decisions.clear();
        for (int offset = 1; offset <= 3; offset++) {
            int index = (from.index() + offset) % 4;
            Seat seat = seats[index];
            List<CallOption> options = computeCallOptions(seat, from, tile, offset);
            if (!options.isEmpty()) {
                pendingCalls.put(index, options);
            }
        }
        if (pendingCalls.isEmpty()) {
            advanceAfterDiscard();
            return;
        }
        phase = Phase.CALLS;
        for (Map.Entry<Integer, List<CallOption>> entry : pendingCalls.entrySet()) {
            Seat seat = seats[entry.getKey()];
            for (TableListener listener : listeners) {
                listener.onCallOptions(this, seat, entry.getValue(), tile, from);
            }
        }
    }

    private List<CallOption> computeCallOptions(Seat seat, Seat from, Tile tile, int offset) {
        List<CallOption> options = new ArrayList<>();
        if (canRon(seat, tile, false)) {
            options.add(CallOption.of(CallType.RON));
        }
        if (seat.riichi() || wall.remaining() <= 0) {
            return options;
        }

        int count = countIdInHand(seat, tile.id());
        if (count >= 2) {
            options.add(new CallOption(CallType.PON, pickTiles(seat, tile.id(), 2), tile));
        }
        if (count >= 3 && wall.canKan()) {
            options.add(new CallOption(CallType.KAN, pickTiles(seat, tile.id(), 3), tile));
        }
        if (offset == 1 && !tile.isHonor()) {
            options.addAll(chiOptions(seat, tile));
        }
        return options;
    }

    private List<CallOption> chiOptions(Seat seat, Tile tile) {
        List<CallOption> options = new ArrayList<>();
        int id = tile.id();
        int number = tile.number();
        int[][] patterns = {{-2, -1}, {-1, 1}, {1, 2}};
        for (int[] pattern : patterns) {
            int firstNumber = number + pattern[0];
            int secondNumber = number + pattern[1];
            if (firstNumber < 1 || firstNumber > 9 || secondNumber < 1 || secondNumber > 9) {
                continue;
            }
            Tile first = findInHand(seat, id + pattern[0]);
            Tile second = findInHand(seat, id + pattern[1]);
            if (first != null && second != null) {
                options.add(new CallOption(CallType.CHI, List.of(first, second), tile));
            }
        }
        return options;
    }

    private Tile findInHand(Seat seat, int tileId) {
        Tile fallback = null;
        for (Tile tile : seat.hand()) {
            if (tile.id() == tileId) {
                if (!tile.isRed()) {
                    return tile;
                }
                fallback = tile;
            }
        }
        return fallback;
    }

    private int countIdInHand(Seat seat, int tileId) {
        int count = 0;
        for (Tile tile : seat.hand()) {
            if (tile.id() == tileId) {
                count++;
            }
        }
        return count;
    }

    private List<Tile> pickTiles(Seat seat, int tileId, int count) {
        List<Tile> picked = new ArrayList<>();
        // 優先留下赤寶牌
        for (Tile tile : seat.hand()) {
            if (tile.id() == tileId && !tile.isRed() && picked.size() < count) {
                picked.add(tile);
            }
        }
        for (Tile tile : seat.hand()) {
            if (tile.id() == tileId && tile.isRed() && picked.size() < count) {
                picked.add(tile);
            }
        }
        return picked;
    }

    private boolean canRon(Seat seat, Tile tile, boolean chankan) {
        if (seat.index() == lastDiscardSeat && !chankan) {
            return false;
        }
        if (!seat.waits().contains(tile.id())) {
            return false;
        }
        if (seat.isFuriten()) {
            return false;
        }
        WinContext ctx = buildWinContext(seat, tile, false, chankan);
        HandScore score = YakuEvaluator.evaluate(ctx);
        return score != null && score.hasYaku();
    }

    /**
     * 宣告鳴牌 / 榮和 / 略過。
     */
    public boolean declareCall(int seatIndex, CallOption option) {
        if (phase != Phase.CALLS || !pendingCalls.containsKey(seatIndex)) {
            return false;
        }
        if (option.type() != CallType.PASS) {
            boolean allowed = pendingCalls.get(seatIndex).stream()
                    .anyMatch(candidate -> candidate.type() == option.type()
                            && candidate.handTiles().equals(option.handTiles()));
            if (!allowed) {
                return false;
            }
        }
        decisions.put(seatIndex, option);
        if (decisions.size() == pendingCalls.size()) {
            resolveCalls();
        }
        return true;
    }

    /** 逾時：把還沒回應的人都當作略過。 */
    public void passRemaining() {
        if (phase != Phase.CALLS) {
            return;
        }
        for (Integer seatIndex : pendingCalls.keySet()) {
            decisions.putIfAbsent(seatIndex, CallOption.of(CallType.PASS));
        }
        resolveCalls();
    }

    private void resolveCalls() {
        List<Integer> ronSeats = new ArrayList<>();
        for (Map.Entry<Integer, CallOption> entry : decisions.entrySet()) {
            if (entry.getValue().type() == CallType.RON) {
                ronSeats.add(entry.getKey());
            }
        }

        // 沒有榮和的人，若原本能榮和卻略過 -> 同巡振聽
        for (Map.Entry<Integer, List<CallOption>> entry : pendingCalls.entrySet()) {
            int index = entry.getKey();
            boolean couldRon = entry.getValue().stream().anyMatch(option -> option.type() == CallType.RON);
            if (couldRon && !ronSeats.contains(index)) {
                Seat seat = seats[index];
                seat.setTemporaryFuriten(true);
                if (seat.riichi()) {
                    seat.setRiichiFuriten();
                }
            }
        }

        if (!ronSeats.isEmpty()) {
            handleRon(ronSeats);
            return;
        }

        CallOption best = null;
        int bestSeat = -1;
        for (Map.Entry<Integer, CallOption> entry : decisions.entrySet()) {
            CallOption option = entry.getValue();
            if (option.type() == CallType.PASS) {
                continue;
            }
            if (best == null || option.type().priority() > best.type().priority()) {
                best = option;
                bestSeat = entry.getKey();
            }
        }

        pendingCalls.clear();
        decisions.clear();

        if (best == null) {
            advanceAfterDiscard();
            return;
        }
        executeCall(bestSeat, best);
    }

    private void handleRon(List<Integer> ronSeats) {
        if (ronSeats.size() >= 3 && rules.multipleRon()) {
            pendingCalls.clear();
            decisions.clear();
            abortiveDraw("三家和");
            return;
        }
        boolean chankan = awaitingChankan;
        Tile winTile = chankan ? chankanTile : lastDiscard;
        int loser = chankan ? chankanSeat : lastDiscardSeat;

        ronSeats.sort(Comparator.comparingInt(index -> Math.floorMod(index - loser, 4)));
        List<PendingWin> wins = new ArrayList<>();
        for (int index : ronSeats) {
            Seat seat = seats[index];
            HandScore score = YakuEvaluator.evaluate(buildWinContext(seat, winTile, false, chankan));
            if (score == null || !score.hasYaku()) {
                continue;
            }
            List<Tile> concealed = new ArrayList<>(seat.hand());
            concealed.add(winTile);
            wins.add(new PendingWin(index, loser, winTile, concealed, score));
        }
        pendingCalls.clear();
        decisions.clear();
        awaitingChankan = false;
        if (wins.isEmpty()) {
            advanceAfterDiscard();
            return;
        }
        if (!rules.multipleRon() && wins.size() > 1) {
            wins = List.of(wins.getFirst());
        }
        if (chankan) {
            // 搶槓成立，槓不生效
            Seat kanSeat = seats[loser];
            revertKan(kanSeat, winTile);
        }
        finishWithWins(wins);
    }

    private void revertKan(Seat seat, Tile tile) {
        for (int i = seat.melds().size() - 1; i >= 0; i--) {
            Meld meld = seat.melds().get(i);
            if (meld.baseTileId() != tile.id()) {
                continue;
            }
            if (meld.type() == MeldType.KAKAN) {
                List<Tile> tiles = new ArrayList<>(meld.tiles());
                tiles.removeLast();
                seat.melds().set(i, Meld.pon(tiles, meld.calledTile(), meld.fromSeat()));
                totalKans--;
                return;
            }
            if (meld.type() == MeldType.ANKAN) {
                seat.melds().remove(i);
                totalKans--;
                return;
            }
        }
    }

    private void executeCall(int seatIndex, CallOption option) {
        Seat seat = seats[seatIndex];
        Seat from = seats[lastDiscardSeat];
        Tile target = lastDiscard;
        from.discards().getLast().markCalled();

        for (Tile tile : option.handTiles()) {
            seat.removeTile(tile);
        }
        List<Tile> tiles = new ArrayList<>(option.handTiles());
        tiles.add(target);

        Meld meld = switch (option.type()) {
            case CHI -> Meld.chi(tiles, target, from.index());
            case PON -> Meld.pon(tiles, target, from.index());
            case KAN -> Meld.minkan(tiles, target, from.index());
            default -> throw new IllegalStateException("Unexpected call: " + option.type());
        };
        seat.addMeld(meld);
        seat.refreshWaits();
        anyCallMade = true;
        clearFirstTurnAll();
        clearIppatsuAll();
        notifyCall(seat, meld);

        if (option.type() == CallType.KAN) {
            totalKans++;
            wall.revealNewDora();
            notifyNewDora();
            if (totalKans >= 4 && rules.abortiveDraws() && !isFourKansBySamePlayer()) {
                abortiveDraw("四槓散了");
                return;
            }
            currentSeat = seatIndex;
            drawAndStartTurn(true);
            return;
        }

        currentSeat = seatIndex;
        rinshanDraw = false;
        startTurn();
    }

    private boolean startChankanCheck(Seat kanSeat, Tile tile, MeldType type) {
        pendingCalls.clear();
        decisions.clear();
        for (int offset = 1; offset <= 3; offset++) {
            int index = (kanSeat.index() + offset) % 4;
            Seat seat = seats[index];
            if (type == MeldType.ANKAN) {
                // 只有國士無雙可以搶暗槓
                int[] counts = Tile.toCounts(seat.hand());
                counts[tile.id()]++;
                if (!HandParser.isThirteenOrphans(counts) || seat.isFuriten()) {
                    continue;
                }
            } else if (!canRon(seat, tile, true)) {
                continue;
            }
            pendingCalls.put(index, List.of(CallOption.of(CallType.RON)));
        }
        if (pendingCalls.isEmpty()) {
            return false;
        }
        awaitingChankan = true;
        chankanSeat = kanSeat.index();
        chankanTile = tile;
        chankanMeldType = type;
        phase = Phase.CALLS;
        for (Map.Entry<Integer, List<CallOption>> entry : pendingCalls.entrySet()) {
            Seat seat = seats[entry.getKey()];
            for (TableListener listener : listeners) {
                listener.onCallOptions(this, seat, entry.getValue(), tile, kanSeat);
            }
        }
        return true;
    }

    private void advanceAfterDiscard() {
        pendingCalls.clear();
        decisions.clear();
        if (awaitingChankan) {
            // 沒有人搶槓，槓成立
            awaitingChankan = false;
            Seat kanSeat = seats[chankanSeat];
            wall.revealNewDora();
            notifyNewDora();
            afterKan(kanSeat);
            return;
        }
        seats[currentSeat].clearFirstTurn();
        if (wall.isExhausted()) {
            exhaustiveDraw();
            return;
        }
        currentSeat = (currentSeat + 1) % 4;
        rinshanDraw = false;
        drawAndStartTurn(false);
    }

    private void clearIppatsuAll() {
        for (Seat seat : seats) {
            seat.clearIppatsu();
        }
    }

    private void clearFirstTurnAll() {
        for (Seat seat : seats) {
            seat.clearFirstTurn();
        }
    }

    private void notifyCall(Seat seat, Meld meld) {
        for (TableListener listener : listeners) {
            listener.onCall(this, seat, meld);
        }
    }

    private void notifyNewDora() {
        List<Tile> indicators = wall.doraIndicators();
        Tile latest = indicators.getLast();
        for (TableListener listener : listeners) {
            listener.onNewDora(this, latest);
        }
    }

    // ------------------------------------------------------------------
    // 和牌情報
    // ------------------------------------------------------------------

    private WinContext buildWinContext(Seat seat, Tile winTile, boolean tsumo) {
        return buildWinContext(seat, winTile, tsumo, false);
    }

    private WinContext buildWinContext(Seat seat, Tile winTile, boolean tsumo, boolean chankan) {
        List<Tile> concealed = new ArrayList<>(seat.hand());
        concealed.add(winTile);
        boolean lastTile = wall.remaining() == 0;
        boolean firstTurn = seat.firstUninterruptedTurn() && seat.discards().isEmpty() && !anyCallMade;
        return WinContext.builder()
                .concealed(concealed)
                .melds(seat.melds())
                .winTile(winTile)
                .tsumo(tsumo)
                .seatWind(seat.wind())
                .roundWind(roundWind)
                .riichi(seat.riichi())
                .doubleRiichi(seat.doubleRiichi())
                .ippatsu(seat.ippatsu())
                .haitei(tsumo && lastTile && !rinshanDraw)
                .houtei(!tsumo && lastTile && !chankan)
                .rinshan(tsumo && rinshanDraw)
                .chankan(chankan)
                .tenhou(tsumo && firstTurn && seat.index() == dealerSeat)
                .chiihou(tsumo && firstTurn && seat.index() != dealerSeat)
                .doraIndicators(wall.doraIndicators())
                .uraIndicators(wall.uraIndicators())
                .rules(rules)
                .build();
    }

    private record PendingWin(int seat, int fromSeat, Tile winTile, List<Tile> concealed, HandScore score) {
    }

    // ------------------------------------------------------------------
    // 結算
    // ------------------------------------------------------------------

    private void finishWithWins(List<PendingWin> wins) {
        int[] deltas = new int[4];
        List<WinEntry> entries = new ArrayList<>();
        boolean dealerWon = false;
        boolean first = true;

        for (PendingWin win : wins) {
            Seat seat = seats[win.seat()];
            boolean dealerWin = win.seat() == dealerSeat;
            dealerWon |= dealerWin;
            int extraHonba = first ? honba : 0;
            int gain = 0;
            if (win.fromSeat() < 0) {
                ScoreCalculator.TsumoPayment payment =
                        ScoreCalculator.tsumoPayment(win.score().basePoints(), dealerWin, extraHonba);
                for (int i = 0; i < 4; i++) {
                    if (i == win.seat()) {
                        continue;
                    }
                    int pay = dealerWin || i != dealerSeat ? payment.fromNonDealer() : payment.fromDealer();
                    deltas[i] -= pay;
                    gain += pay;
                }
            } else {
                gain = ScoreCalculator.ronPayment(win.score().basePoints(), dealerWin, extraHonba);
                deltas[win.fromSeat()] -= gain;
            }
            if (first) {
                gain += riichiSticks * 1000;
                riichiSticks = 0;
                first = false;
            }
            deltas[win.seat()] += gain;
            entries.add(new WinEntry(win.seat(), win.fromSeat(), win.winTile(), win.concealed(),
                    List.copyOf(seat.melds()), win.score(), gain));
        }

        applyDeltas(deltas);
        boolean dealerKeeps = dealerWon;
        RoundResult result = new RoundResult(wins.getFirst().fromSeat() < 0 ? RoundResult.Type.TSUMO
                : RoundResult.Type.RON, entries, List.of(), combineWithRoundDeltas(deltas), "", dealerKeeps, false);
        endRound(result, dealerKeeps, true);
    }

    private void exhaustiveDraw() {
        List<Integer> tenpai = new ArrayList<>();
        for (Seat seat : seats) {
            seat.refreshWaits();
            if (seat.isTenpai()) {
                tenpai.add(seat.index());
            }
        }
        int[] deltas = new int[4];
        if (!tenpai.isEmpty() && tenpai.size() < 4) {
            int gain = 3000 / tenpai.size();
            int pay = 3000 / (4 - tenpai.size());
            for (int i = 0; i < 4; i++) {
                deltas[i] += tenpai.contains(i) ? gain : -pay;
            }
        }
        applyDeltas(deltas);
        boolean dealerKeeps = rules.tenpaiRenchan() && tenpai.contains(dealerSeat);
        RoundResult result = new RoundResult(RoundResult.Type.EXHAUSTIVE_DRAW, List.of(), tenpai,
                combineWithRoundDeltas(deltas), "荒牌流局", dealerKeeps, false);
        endRound(result, dealerKeeps, false);
    }

    private void abortiveDraw(String reason) {
        int[] deltas = new int[4];
        RoundResult result = new RoundResult(RoundResult.Type.ABORTIVE_DRAW, List.of(), List.of(),
                combineWithRoundDeltas(deltas), reason, true, false);
        endRound(result, true, false);
    }

    private void applyDeltas(int[] deltas) {
        for (int i = 0; i < 4; i++) {
            seats[i].addPoints(deltas[i]);
        }
    }

    /** 把結算的收支與這一局先付出的立直棒合併，作為顯示用的增減。 */
    private int[] combineWithRoundDeltas(int[] deltas) {
        int[] combined = new int[4];
        for (int i = 0; i < 4; i++) {
            combined[i] = deltas[i] + roundDeltas[i];
        }
        return combined;
    }

    private void endRound(RoundResult result, boolean dealerKeeps, boolean someoneWon) {
        phase = Phase.ROUND_ENDED;
        lastResult = result;
        for (TableListener listener : listeners) {
            listener.onRoundEnd(this, result);
        }
        prepareNextRound(dealerKeeps, someoneWon);
    }

    private void prepareNextRound(boolean dealerKeeps, boolean someoneWon) {
        if (dealerKeeps) {
            honba++;
        } else {
            honba = someoneWon ? 0 : honba + 1;
            dealerSeat = (dealerSeat + 1) % 4;
            roundNumber++;
            if (roundNumber > 4) {
                roundNumber = 1;
                roundWind = roundWind.next();
            }
        }
        if (isGameOver()) {
            phase = Phase.GAME_ENDED;
            for (TableListener listener : listeners) {
                listener.onGameEnd(this);
            }
        }
    }

    private boolean isGameOver() {
        if (rules.endOnBankrupt()) {
            for (Seat seat : seats) {
                if (seat.points() < 0) {
                    return true;
                }
            }
        }
        return roundWind.ordinal() >= rules.targetRounds();
    }

    /** 依點數排序後的最終名次。 */
    public List<Seat> standings() {
        List<Seat> sorted = new ArrayList<>(List.of(seats));
        sorted.sort(Comparator.comparingInt(Seat::points).reversed());
        return sorted;
    }

    /** 目前的鳴牌是否正在等待搶槓判定。 */
    public boolean awaitingChankan() {
        return awaitingChankan;
    }

    public MeldType chankanMeldType() {
        return chankanMeldType;
    }
}
