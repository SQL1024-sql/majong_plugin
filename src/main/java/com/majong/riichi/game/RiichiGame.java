package com.majong.riichi.game;

import com.majong.riichi.core.Flower;
import com.majong.riichi.core.Hand;
import com.majong.riichi.core.HandEvaluator;
import com.majong.riichi.core.HandValue;
import com.majong.riichi.core.Piece;
import com.majong.riichi.core.Meld;
import com.majong.riichi.core.MeldType;
import com.majong.riichi.core.ScoreCalculator;
import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.core.Wall;
import com.majong.riichi.core.WinChecker;
import com.majong.riichi.core.WinContext;
import com.majong.riichi.taiwan.TaiwanContext;
import com.majong.riichi.taiwan.TaiwanRules;
import com.majong.riichi.taiwan.TaiwanScorer;
import com.majong.riichi.taiwan.TaiwanValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * A four player riichi mahjong game.
 *
 * <p>The engine never blocks. It exposes whose turn it is and what that player
 * may legally do, and the caller feeds decisions back in through
 * {@link #act(int, Action)}. Everything that happens along the way is reported
 * to a {@link GameListener} so a front end can render it.
 */
public final class RiichiGame {

    /** What the table is waiting for. */
    public enum Phase {
        /** No hand in progress. */
        IDLE,
        /** The player whose turn it is must act. */
        ACT,
        /** One or more players may claim the tile just discarded. */
        CALL,
        /** The game is over. */
        FINISHED
    }

    public static final int SEATS = 4;

    private final GameRules rules;
    private final GameListener listener;
    private final Random random;
    private final SeatState[] seats = new SeatState[SEATS];

    private Wall wall;
    private Phase phase = Phase.IDLE;
    private int dealer;
    private int roundWind = Tiles.EAST;
    private int handNumber = 1;
    private int honba;
    private int riichiSticks;

    private int current;
    private Tile lastDiscard;
    private int lastDiscardSeat = -1;
    private boolean chankanWindow;
    private Meld pendingKan;
    private int pendingKanSeat = -1;

    private final Map<Integer, Action> responses = new LinkedHashMap<>();
    private final List<Integer> pendingSeats = new ArrayList<>();

    private boolean anyCallMade;
    private boolean rinshanDraw;
    private boolean fourKanAbort;
    private HandResult lastResult;
    private boolean gameOver;
    /** Taiwanese stakes; unused when a japanese hand is being dealt. */
    private TaiwanRules taiwanRules = TaiwanRules.standard();

    public RiichiGame(GameRules rules, List<String> names, long seed, GameListener listener) {
        if (names.size() != SEATS) {
            throw new IllegalArgumentException("a table seats exactly four players");
        }
        this.rules = rules;
        this.listener = listener == null ? new GameListener() {
        } : listener;
        this.random = new Random(seed);
        for (int seat = 0; seat < SEATS; seat++) {
            seats[seat] = new SeatState(seat, names.get(seat), rules.startingPoints(),
                    rules.variant().totalSets());
        }
    }

    /** Sets the base and tai a taiwanese table plays for. */
    public void taiwanRules(TaiwanRules value) {
        this.taiwanRules = value;
    }

    public Variant variant() {
        return rules.variant();
    }

    private int totalSets() {
        return rules.variant().totalSets();
    }

    // ---------------------------------------------------------------- state

    public Phase phase() {
        return phase;
    }

    public SeatState seat(int seat) {
        return seats[seat];
    }

    public int currentSeat() {
        return current;
    }

    public int dealer() {
        return dealer;
    }

    public int roundWind() {
        return roundWind;
    }

    public int handNumber() {
        return handNumber;
    }

    public int honba() {
        return honba;
    }

    public int riichiSticks() {
        return riichiSticks;
    }

    public int wallRemaining() {
        return wall == null ? 0 : wall.remaining();
    }

    public List<Tile> doraIndicators() {
        return wall == null ? List.of() : wall.doraIndicators();
    }

    public Tile lastDiscard() {
        return lastDiscard;
    }

    public int lastDiscardSeat() {
        return lastDiscardSeat;
    }

    public HandResult lastResult() {
        return lastResult;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    /** Seats the table is currently waiting on. */
    public List<Integer> pendingSeats() {
        return Collections.unmodifiableList(pendingSeats);
    }

    public int seatWind(int seat) {
        return Tiles.EAST + Math.floorMod(seat - dealer, SEATS);
    }

    /** Round and hand as it is usually written, for example {@code 東1局 2本場}. */
    public String roundName() {
        return Tiles.display(roundWind) + handNumber + "局 " + honba + "本場";
    }

    // ------------------------------------------------------------ hand flow

    /** Deals a fresh hand. */
    public void startHand() {
        if (gameOver) {
            throw new IllegalStateException("the game is already over");
        }
        for (SeatState seat : seats) {
            seat.resetForHand();
        }
        wall = rules.variant().hasFlowers()
                ? Wall.shuffledWithFlowers(random)
                : Wall.shuffled(random, rules.redFives());
        anyCallMade = false;
        fourKanAbort = false;
        chankanWindow = false;
        pendingKan = null;
        pendingKanSeat = -1;
        lastDiscard = null;
        lastDiscardSeat = -1;
        responses.clear();
        pendingSeats.clear();
        lastResult = null;

        for (int round = 0; round < rules.variant().handSize(); round++) {
            for (int offset = 0; offset < SEATS; offset++) {
                dealOne(seats[(dealer + offset) % SEATS]);
            }
        }
        phase = Phase.ACT;
        listener.onHandStarted(this);
        drawFor(dealer, false);
    }

    /** Deals one tile, setting aside and replacing any flower that turns up. */
    private void dealOne(SeatState state) {
        Piece piece = wall.drawPiece();
        while (piece.isFlower()) {
            state.addFlower(piece.flower());
            listener.onFlower(this, state.seat(), piece.flower());
            piece = wall.drawReplacementPiece();
        }
        state.hand().add(piece.tile());
    }

    private void drawFor(int seat, boolean replacement) {
        SeatState state = seats[seat];
        if (!replacement && wall.isExhausted()) {
            finishExhaustiveDraw();
            return;
        }
        Piece piece = replacement ? wall.drawReplacementPiece() : wall.drawPiece();
        while (piece.isFlower()) {
            // A flower is turned face up and replaced from the back of the wall.
            state.addFlower(piece.flower());
            listener.onFlower(this, seat, piece.flower());
            replacement = true;
            piece = wall.drawReplacementPiece();
        }
        Tile tile = piece.tile();
        state.hand().draw(tile);
        state.markDrawn();
        state.setTemporaryFuriten(false);
        rinshanDraw = replacement;
        current = seat;
        phase = Phase.ACT;
        listener.onTurn(this, seat, tile);
    }

    // -------------------------------------------------------------- options

    /** Everything the given seat may legally do right now. */
    public List<Action> options(int seat) {
        if (phase == Phase.ACT && seat == current) {
            return turnOptions(seat);
        }
        if (phase == Phase.CALL && pendingSeats.contains(seat) && !responses.containsKey(seat)) {
            return callOptions(seat);
        }
        return List.of();
    }

    private List<Action> turnOptions(int seat) {
        SeatState state = seats[seat];
        Hand hand = state.hand();
        List<Action> actions = new ArrayList<>();

        if (canDeclareTsumo(seat)) {
            actions.add(new Action.Tsumo());
        }
        if (rules.variant() == Variant.JAPANESE
                && isFirstUninterruptedTurn(state) && state.hasNineTerminals()) {
            actions.add(new Action.NineTerminals());
        }
        for (int kind : kanKinds(state)) {
            actions.add(new Action.Kan(kind));
        }

        boolean canRiichi = rules.variant().allowsRiichi() && hand.isClosed() && !state.riichi()
                && state.score() >= 1000 && wall.remaining() >= 4;
        List<Tile> candidates = distinct(hand.allConcealed());
        for (Tile tile : candidates) {
            if (state.riichi() && !tile.equals(hand.drawn())) {
                // A player under riichi may only put the tile they just drew down.
                continue;
            }
            actions.add(new Action.Discard(tile, false));
            if (canRiichi && leavesTenpai(hand, tile)) {
                actions.add(new Action.Discard(tile, true));
            }
        }
        return actions;
    }

    private List<Action> callOptions(int seat) {
        SeatState state = seats[seat];
        Tile tile = chankanWindow ? pendingKan.tiles().getFirst() : lastDiscard;
        int fromSeat = chankanWindow ? pendingKanSeat : lastDiscardSeat;
        if (tile == null || seat == fromSeat) {
            return List.of();
        }
        List<Action> actions = new ArrayList<>();
        if (canDeclareRon(seat, tile)) {
            actions.add(new Action.Ron());
        }
        if (!chankanWindow && !state.riichi() && !wall.isExhausted()) {
            int held = state.hand().count(tile.kind());
            if (held >= 2) {
                actions.add(new Action.Pon());
            }
            if (held >= 3 && wall.canDeclareKan()) {
                actions.add(new Action.Kan(tile.kind()));
            }
            if (seat == (fromSeat + 1) % SEATS) {
                for (Action.Chi chi : chiOptions(state, tile)) {
                    actions.add(chi);
                }
            }
        }
        if (!actions.isEmpty()) {
            actions.add(new Action.Pass());
        }
        return actions;
    }

    private List<Action.Chi> chiOptions(SeatState state, Tile tile) {
        List<Action.Chi> options = new ArrayList<>();
        if (Tiles.isHonor(tile.kind())) {
            return options;
        }
        int rank = Tiles.rankOf(tile.kind());
        int[][] shapes = {{-2, -1}, {-1, 1}, {1, 2}};
        for (int[] shape : shapes) {
            int firstRank = rank + shape[0];
            int secondRank = rank + shape[1];
            if (firstRank < 1 || firstRank > 9 || secondRank < 1 || secondRank > 9) {
                continue;
            }
            int first = tile.kind() + shape[0];
            int second = tile.kind() + shape[1];
            Tile firstTile = findTile(state, first);
            Tile secondTile = findTile(state, second);
            if (firstTile != null && secondTile != null) {
                options.add(new Action.Chi(firstTile, secondTile));
            }
        }
        return options;
    }

    private Tile findTile(SeatState state, int kind) {
        for (Tile tile : state.hand().concealed()) {
            if (tile.kind() == kind) {
                return tile;
            }
        }
        return null;
    }

    private List<Integer> kanKinds(SeatState state) {
        List<Integer> kinds = new ArrayList<>();
        if (!wall.canDeclareKan()) {
            return kinds;
        }
        int[] counts = state.hand().countsWithDrawn();
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            if (counts[kind] == 4) {
                if (!state.riichi() || keepsWaitsAfterKan(state, kind)) {
                    kinds.add(kind);
                }
            }
        }
        if (!state.riichi()) {
            for (Meld meld : state.hand().melds()) {
                if (meld.type() == MeldType.PON
                        && counts[meld.baseKind()] >= 1) {
                    kinds.add(meld.baseKind());
                }
            }
        }
        return kinds;
    }

    /** A player under riichi may only kan when it leaves the wait untouched. */
    private boolean keepsWaitsAfterKan(SeatState state, int kind) {
        Hand hand = state.hand();
        int[] before = hand.counts();
        int[] after = hand.countsWithDrawn();
        after[kind] -= 4;
        Set<Integer> waitsBefore = WinChecker.waits(before, hand.melds().size(), totalSets());
        Set<Integer> waitsAfter = WinChecker.waits(after, hand.melds().size() + 1, totalSets());
        return waitsBefore.equals(waitsAfter);
    }

    private boolean leavesTenpai(Hand hand, Tile discard) {
        int[] counts = hand.countsWithDrawn();
        counts[discard.kind()]--;
        return WinChecker.isTenpai(counts, hand.melds().size(), totalSets());
    }

    private boolean isFirstUninterruptedTurn(SeatState state) {
        return !anyCallMade && state.discards().isEmpty();
    }

    private static List<Tile> distinct(List<Tile> tiles) {
        List<Tile> distinct = new ArrayList<>();
        for (Tile tile : tiles) {
            if (!distinct.contains(tile)) {
                distinct.add(tile);
            }
        }
        return distinct;
    }

    // --------------------------------------------------------------- acting

    /**
     * Applies a decision. Returns {@code false} when the action is not legal
     * for that seat right now, leaving the game untouched.
     */
    public boolean act(int seat, Action action) {
        if (!options(seat).contains(action)) {
            return false;
        }
        if (phase == Phase.ACT) {
            return actOnTurn(seat, action);
        }
        responses.put(seat, action);
        if (responses.size() == pendingSeats.size()) {
            resolveCalls();
        }
        return true;
    }

    private boolean actOnTurn(int seat, Action action) {
        switch (action) {
            case Action.Tsumo ignored -> {
                Tile drawn = seats[seat].hand().drawn();
                if (rules.variant() == Variant.TAIWANESE) {
                    finishTaiwaneseWin(seat, -1, drawn, true, false);
                } else {
                    finishWithWin(seat, -1, buildContext(seat, drawn, true, false));
                }
            }
            case Action.NineTerminals ignored -> finishAbortive(AbortReason.NINE_TERMINALS);
            case Action.Kan kan -> declareKan(seat, kan.kind());
            case Action.Discard discard -> discard(seat, discard.tile(), discard.riichi());
            default -> {
                return false;
            }
        }
        return true;
    }

    private void discard(int seat, Tile tile, boolean riichi) {
        SeatState state = seats[seat];
        Hand hand = state.hand();
        Tile discarded;
        if (tile.equals(hand.drawn())) {
            discarded = hand.discardDrawn();
        } else {
            discarded = hand.removeExact(tile);
        }
        hand.keepDrawn();

        boolean wasRiichiDeclaration = false;
        if (riichi) {
            boolean isDouble = isFirstUninterruptedTurn(state);
            state.declareRiichi(isDouble);
            state.addScore(-1000);
            riichiSticks++;
            wasRiichiDeclaration = true;
        }
        state.addDiscard(discarded);
        if (!wasRiichiDeclaration) {
            state.clearIppatsu();
        }
        lastDiscard = discarded;
        lastDiscardSeat = seat;
        rinshanDraw = false;
        listener.onDiscard(this, seat, discarded, wasRiichiDeclaration);
        openCallWindow(false);
    }

    private void declareKan(int seat, int kind) {
        SeatState state = seats[seat];
        Hand hand = state.hand();
        Meld existingPon = null;
        for (Meld meld : hand.melds()) {
            if (meld.type() == MeldType.PON && meld.baseKind() == kind) {
                existingPon = meld;
                break;
            }
        }
        if (existingPon != null) {
            Tile fourth = hand.remove(kind);
            Meld upgraded = existingPon.upgradeToKan(fourth);
            hand.replaceMeld(existingPon, upgraded);
            listener.onCall(this, seat, upgraded);
            // Anyone waiting on that tile may snatch it out of the kan.
            pendingKan = upgraded;
            pendingKanSeat = seat;
            openCallWindow(true);
            return;
        }
        List<Tile> tiles = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            tiles.add(hand.remove(kind));
        }
        Meld ankan = Meld.ankan(tiles);
        hand.addMeld(ankan);
        listener.onCall(this, seat, ankan);
        completeKan(seat);
    }

    private void completeKan(int seat) {
        clearAllIppatsu();
        wall.revealKanIndicator();
        listener.onDoraRevealed(this, wall.doraIndicators().getLast());
        // Four kans end the hand unless they all belong to the same player.
        if (totalKans() >= 4 && countKanOwners() > 1) {
            fourKanAbort = true;
        }
        drawFor(seat, true);
    }

    private int totalKans() {
        int kans = 0;
        for (SeatState state : seats) {
            kans += state.hand().kanCount();
        }
        return kans;
    }

    private int countKanOwners() {
        int owners = 0;
        for (SeatState state : seats) {
            if (state.hand().kanCount() > 0) {
                owners++;
            }
        }
        return owners;
    }

    // ---------------------------------------------------------- call window

    private void openCallWindow(boolean chankan) {
        chankanWindow = chankan;
        responses.clear();
        pendingSeats.clear();
        int source = chankan ? pendingKanSeat : lastDiscardSeat;
        for (int offset = 1; offset < SEATS; offset++) {
            int seat = (source + offset) % SEATS;
            if (!callOptions(seat).isEmpty()) {
                pendingSeats.add(seat);
            }
        }
        if (pendingSeats.isEmpty()) {
            resolveCalls();
            return;
        }
        phase = Phase.CALL;
        listener.onCallWindow(this, List.copyOf(pendingSeats));
    }

    private void resolveCalls() {
        List<Integer> ronSeats = new ArrayList<>();
        Integer ponOrKanSeat = null;
        Integer chiSeat = null;
        Action.Chi chi = null;
        Action.Kan kan = null;

        int source = chankanWindow ? pendingKanSeat : lastDiscardSeat;
        for (int offset = 1; offset < SEATS; offset++) {
            int seat = (source + offset) % SEATS;
            Action response = responses.get(seat);
            if (response == null) {
                continue;
            }
            switch (response) {
                case Action.Ron ignored -> ronSeats.add(seat);
                case Action.Pon ignored -> {
                    if (ponOrKanSeat == null) {
                        ponOrKanSeat = seat;
                    }
                }
                case Action.Kan value -> {
                    if (ponOrKanSeat == null) {
                        ponOrKanSeat = seat;
                        kan = value;
                    }
                }
                case Action.Chi value -> {
                    if (chiSeat == null) {
                        chiSeat = seat;
                        chi = value;
                    }
                }
                default -> {
                }
            }
        }

        // Everyone who let a winning tile go by is furiten until their next draw.
        for (int seat : pendingSeats) {
            Action response = responses.get(seat);
            if (!(response instanceof Action.Ron) && couldHaveWon(seat)) {
                seats[seat].setTemporaryFuriten(true);
                if (seats[seat].riichi()) {
                    seats[seat].setRiichiFuriten();
                }
            }
        }

        boolean chankan = chankanWindow;
        chankanWindow = false;
        pendingSeats.clear();
        responses.clear();

        if (!ronSeats.isEmpty()) {
            if (!rules.headBump() && ronSeats.size() == 3) {
                finishAbortive(AbortReason.THREE_RON);
                return;
            }
            int winner = ronSeats.getFirst();
            Tile tile = chankan ? pendingKan.tiles().getFirst() : lastDiscard;
            int loser = chankan ? pendingKanSeat : lastDiscardSeat;
            if (rules.variant() == Variant.TAIWANESE) {
                finishTaiwaneseWin(winner, loser, tile, false, chankan);
            } else {
                finishWithWin(winner, loser, buildContext(winner, tile, false, chankan));
            }
            return;
        }
        if (chankan) {
            completeKan(pendingKanSeat);
            return;
        }
        if (ponOrKanSeat != null) {
            if (kan != null) {
                applyDaiminkan(ponOrKanSeat);
            } else {
                applyPon(ponOrKanSeat);
            }
            return;
        }
        if (chiSeat != null) {
            applyChi(chiSeat, chi);
            return;
        }
        afterDiscardSettled();
    }

    private boolean couldHaveWon(int seat) {
        Tile tile = chankanWindow ? pendingKan.tiles().getFirst() : lastDiscard;
        if (tile == null) {
            return false;
        }
        return seats[seat].waits().contains(tile.kind());
    }

    private void applyPon(int seat) {
        SeatState state = seats[seat];
        Tile claimed = seats[lastDiscardSeat].takeLastDiscard();
        List<Tile> tiles = new ArrayList<>(3);
        tiles.add(claimed);
        tiles.add(state.hand().remove(claimed.kind()));
        tiles.add(state.hand().remove(claimed.kind()));
        Meld meld = Meld.pon(tiles, claimed, lastDiscardSeat);
        state.hand().addMeld(meld);
        finishCall(seat, meld, false);
    }

    private void applyDaiminkan(int seat) {
        SeatState state = seats[seat];
        Tile claimed = seats[lastDiscardSeat].takeLastDiscard();
        List<Tile> tiles = new ArrayList<>(4);
        tiles.add(claimed);
        for (int i = 0; i < 3; i++) {
            tiles.add(state.hand().remove(claimed.kind()));
        }
        Meld meld = Meld.daiminkan(tiles, claimed, lastDiscardSeat);
        state.hand().addMeld(meld);
        finishCall(seat, meld, true);
    }

    private void applyChi(int seat, Action.Chi chi) {
        SeatState state = seats[seat];
        Tile claimed = seats[lastDiscardSeat].takeLastDiscard();
        List<Tile> tiles = new ArrayList<>(3);
        tiles.add(claimed);
        tiles.add(state.hand().removeExact(chi.first()));
        tiles.add(state.hand().removeExact(chi.second()));
        Meld meld = Meld.chi(tiles, claimed, lastDiscardSeat);
        state.hand().addMeld(meld);
        finishCall(seat, meld, false);
    }

    private void finishCall(int seat, Meld meld, boolean kan) {
        anyCallMade = true;
        clearAllIppatsu();
        lastDiscard = null;
        listener.onCall(this, seat, meld);
        if (kan) {
            completeKan(seat);
            return;
        }
        current = seat;
        phase = Phase.ACT;
        listener.onTurn(this, seat, null);
    }

    private void clearAllIppatsu() {
        for (SeatState state : seats) {
            state.clearIppatsu();
        }
    }

    private void afterDiscardSettled() {
        if (fourKanAbort) {
            finishAbortive(AbortReason.FOUR_KANS);
            return;
        }
        if (allRiichi()) {
            finishAbortive(AbortReason.FOUR_RIICHI);
            return;
        }
        if (isFourWindDiscard()) {
            finishAbortive(AbortReason.FOUR_WINDS);
            return;
        }
        if (wall.isExhausted()) {
            finishExhaustiveDraw();
            return;
        }
        drawFor((lastDiscardSeat + 1) % SEATS, false);
    }

    private boolean allRiichi() {
        for (SeatState state : seats) {
            if (!state.riichi()) {
                return false;
            }
        }
        return true;
    }

    private boolean isFourWindDiscard() {
        if (anyCallMade) {
            return false;
        }
        int wind = -1;
        for (SeatState state : seats) {
            if (state.discards().size() != 1) {
                return false;
            }
            int kind = state.discards().getFirst().kind();
            if (!Tiles.isWind(kind)) {
                return false;
            }
            if (wind == -1) {
                wind = kind;
            } else if (wind != kind) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------- winning

    private boolean canDeclareTsumo(int seat) {
        SeatState state = seats[seat];
        Tile drawn = state.hand().drawn();
        if (drawn == null) {
            return false;
        }
        return scores(seat, state.hand(), drawn, true, false);
    }

    /** Whether these tiles are a hand this variant lets you declare. */
    private boolean scores(int seat, Hand hand, Tile winningTile, boolean tsumo, boolean chankan) {
        if (rules.variant() == Variant.TAIWANESE) {
            return TaiwanScorer.score(hand, taiwanContext(seat, winningTile, tsumo, chankan),
                    taiwanRules) != null;
        }
        return HandEvaluator.evaluate(hand, buildContext(seat, winningTile, tsumo, chankan)) != null;
    }

    private boolean canDeclareRon(int seat, Tile tile) {
        SeatState state = seats[seat];
        if (state.isFuriten() || !state.waits().contains(tile.kind())) {
            return false;
        }
        Hand probe = copyOf(state.hand());
        probe.add(tile);
        return scores(seat, probe, tile, false, chankanWindow);
    }

    private Hand copyOf(Hand hand) {
        Hand copy = new Hand();
        for (Tile tile : hand.concealed()) {
            copy.add(tile);
        }
        for (Meld meld : hand.melds()) {
            copy.addMeld(meld);
        }
        return copy;
    }

    private WinContext buildContext(int seat, Tile winningTile, boolean tsumo, boolean chankan) {
        SeatState state = seats[seat];
        boolean firstTurn = !anyCallMade && state.discards().isEmpty();
        return WinContext.builder(winningTile)
                .tsumo(tsumo)
                .seatWind(seatWind(seat))
                .roundWind(roundWind)
                .riichi(state.riichi() && !state.doubleRiichi())
                .doubleRiichi(state.doubleRiichi())
                .ippatsu(state.ippatsu())
                .rinshan(tsumo && rinshanDraw)
                .chankan(chankan)
                .haitei(tsumo && wall.remaining() == 0)
                .houtei(!tsumo && !chankan && wall.remaining() == 0)
                .tenhou(tsumo && firstTurn && seat == dealer)
                .chiihou(tsumo && firstTurn && seat != dealer)
                .openTanyao(rules.openTanyao())
                .doraIndicators(wall.doraIndicators())
                .uraIndicators(wall.uraIndicators())
                .honba(honba)
                .riichiSticks(riichiSticks)
                .build();
    }

    private TaiwanContext taiwanContext(int seat, Tile winningTile, boolean tsumo,
                                        boolean chankan) {
        SeatState state = seats[seat];
        boolean firstTurn = !anyCallMade && state.discards().isEmpty();
        return TaiwanContext.builder(winningTile)
                .tsumo(tsumo)
                .seatWind(seatWind(seat))
                .roundWind(roundWind)
                // A taiwanese dealer keeps their seat rather than banking honba.
                .dealerStreak(seat == dealer ? honba : 0)
                .flowers(state.flowers())
                .afterKan(tsumo && rinshanDraw)
                .robbingKan(chankan)
                .lastDraw(tsumo && wall.remaining() == 0)
                .lastDiscard(!tsumo && !chankan && wall.remaining() == 0)
                .firstTurn(firstTurn)
                .build();
    }

    /** Settles a taiwanese win: base plus tai, from one player or from all three. */
    private void finishTaiwaneseWin(int winner, int loser, Tile winningTile, boolean tsumo,
                                    boolean chankan) {
        SeatState state = seats[winner];
        if (!tsumo) {
            state.hand().add(winningTile);
        }
        TaiwanValue value = TaiwanScorer.score(state.hand(),
                taiwanContext(winner, winningTile, tsumo, chankan), taiwanRules);
        Objects.requireNonNull(value, "a declared win must score");

        int[] deltas = new int[SEATS];
        if (tsumo) {
            for (int seat = 0; seat < SEATS; seat++) {
                if (seat != winner) {
                    deltas[seat] = -value.perPlayer();
                }
            }
            deltas[winner] = value.perPlayer() * 3;
        } else {
            deltas[loser] = -value.perPlayer();
            deltas[winner] = value.perPlayer();
        }
        applyDeltas(deltas);

        List<String> lines = new ArrayList<>();
        for (TaiwanValue.ScoredTai pattern : value.patterns()) {
            lines.add(pattern.display());
        }
        settleHand(new HandResult.Won(winner, loser, lines, value.summary(), deltas),
                winner == dealer);
    }

    private void finishWithWin(int winner, int loser, WinContext context) {
        SeatState state = seats[winner];
        if (loser >= 0) {
            state.hand().add(context.winningTile());
        }
        HandValue value = HandEvaluator.evaluate(state.hand(), context);
        Objects.requireNonNull(value, "a declared win must have a yaku");

        int[] deltas = new int[SEATS];
        deltas[winner] = value.payment().total();
        if (loser >= 0) {
            deltas[loser] -= value.payment().fromDiscarder();
        } else {
            for (int seat = 0; seat < SEATS; seat++) {
                if (seat == winner) {
                    continue;
                }
                boolean paysDealerShare = winner != dealer && seat == dealer;
                deltas[seat] -= paysDealerShare
                        ? value.payment().fromDealer() : value.payment().fromNonDealer();
            }
        }
        riichiSticks = 0;
        applyDeltas(deltas);

        List<String> lines = new ArrayList<>();
        for (com.majong.riichi.core.ScoredYaku scored : value.yaku()) {
            lines.add(scored.display());
        }
        if (value.dora() + value.uraDora() + value.redDora() > 0) {
            lines.add("ドラ" + value.dora() + " 裏ドラ" + value.uraDora() + " 赤" + value.redDora());
        }
        settleHand(new HandResult.Won(winner, loser, lines, value.summary(), deltas),
                winner == dealer);
    }

    private void finishExhaustiveDraw() {
        boolean[] tenpai = new boolean[SEATS];
        for (int seat = 0; seat < SEATS; seat++) {
            seats[seat].hand().keepDrawn();
            tenpai[seat] = seats[seat].isTenpai();
        }
        int[] deltas = rules.variant().hasTenpaiPayments()
                ? ScoreCalculator.exhaustiveDrawTransfer(tenpai) : new int[SEATS];
        applyDeltas(deltas);
        settleHand(new HandResult.ExhaustiveDraw(tenpai, deltas), tenpai[dealer]);
    }

    private void finishAbortive(AbortReason reason) {
        settleHand(new HandResult.AbortiveDraw(reason, new int[SEATS]), true);
    }

    private void applyDeltas(int[] deltas) {
        for (int seat = 0; seat < SEATS; seat++) {
            seats[seat].addScore(deltas[seat]);
        }
    }

    private void settleHand(HandResult result, boolean dealerKeepsSeat) {
        lastResult = result;
        phase = Phase.IDLE;
        listener.onHandFinished(this, result);

        if (result instanceof HandResult.Won won && won.winner() != dealer) {
            honba = 0;
        } else {
            honba++;
        }
        if (!dealerKeepsSeat) {
            advanceDealer();
        }
        if (isFinished()) {
            gameOver = true;
            phase = Phase.FINISHED;
            listener.onGameFinished(this);
        }
    }

    private void advanceDealer() {
        dealer = (dealer + 1) % SEATS;
        handNumber++;
        if (handNumber > SEATS) {
            handNumber = 1;
            roundWind++;
        }
    }

    private boolean isFinished() {
        if (roundWind > rules.finalRoundWind()) {
            return true;
        }
        if (rules.endOnBankruptcy()) {
            for (SeatState state : seats) {
                if (state.score() < 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Seats ordered by final score, highest first. */
    public List<SeatState> standings() {
        List<SeatState> ordered = new ArrayList<>(List.of(seats));
        ordered.sort((left, right) -> Integer.compare(right.score(), left.score()));
        return ordered;
    }
}
