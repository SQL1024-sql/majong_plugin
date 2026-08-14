package io.github.sql1024.mahjong.ai;

import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.ShantenCalculator;
import io.github.sql1024.mahjong.core.Tile;
import io.github.sql1024.mahjong.core.Wind;
import io.github.sql1024.mahjong.game.CallOption;
import io.github.sql1024.mahjong.game.CallType;
import io.github.sql1024.mahjong.game.MahjongTable;
import io.github.sql1024.mahjong.game.Seat;
import io.github.sql1024.mahjong.game.TurnOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * 簡單的電腦對手。
 *
 * <p>策略：能自摸 / 榮和就和，聽牌就立直，其餘時候打出最能降低向聽數的牌，
 * 只在役牌或明顯有利時鳴牌。
 */
public final class SimpleBot {

    private SimpleBot() {
    }

    /**
     * 讓所有輪到電腦的座位各做一次決定。
     *
     * @return 是否有電腦做了決定
     */
    public static boolean actOnce(MahjongTable table) {
        switch (table.phase()) {
            case TURN -> {
                Seat seat = table.seat(table.currentSeat());
                if (!seat.isBot()) {
                    return false;
                }
                takeTurn(table, seat, table.currentOptions());
                return true;
            }
            case CALLS -> {
                boolean acted = false;
                for (int index : table.waitingSeats()) {
                    Seat seat = table.seat(index);
                    if (!seat.isBot()) {
                        continue;
                    }
                    respond(table, seat, table.pendingOptions(index));
                    acted = true;
                }
                return acted;
            }
            default -> {
                return false;
            }
        }
    }

    /** 電腦的出牌回合。 */
    public static void takeTurn(MahjongTable table, Seat seat, TurnOptions options) {
        if (options.canTsumo()) {
            table.declareTurnAction(seat.index(), CallOption.of(CallType.TSUMO));
            return;
        }
        if (options.canKyuushu()) {
            table.declareTurnAction(seat.index(), CallOption.of(CallType.KYUUSHU));
            return;
        }
        if (!options.kanOptions().isEmpty() && seat.riichi()) {
            table.declareTurnAction(seat.index(), options.kanOptions().getFirst());
            return;
        }
        if (!options.kanOptions().isEmpty() && shouldKan(seat, options)) {
            table.declareTurnAction(seat.index(), options.kanOptions().getFirst());
            return;
        }
        if (options.canRiichi()) {
            Tile choice = bestDiscard(seat, options.riichiDiscards());
            table.discard(seat.index(), choice, true);
            return;
        }
        if (seat.riichi() && seat.drawn() != null) {
            table.discard(seat.index(), seat.drawn(), false);
            return;
        }
        Tile choice = bestDiscard(seat, seat.fullHand());
        table.discard(seat.index(), choice, false);
    }

    private static boolean shouldKan(Seat seat, TurnOptions options) {
        // 只在不會拆聽的情況下開槓：暗刻已經成形，開槓通常不虧
        return seat.isOpen() || ShantenCalculator.shanten(Tile.toCounts(seat.hand()), seat.melds().size()) <= 1;
    }

    /** 電腦面對別人打牌時的反應。 */
    public static void respond(MahjongTable table, Seat seat, List<CallOption> options) {
        for (CallOption option : options) {
            if (option.type() == CallType.RON) {
                table.declareCall(seat.index(), option);
                return;
            }
        }
        CallOption chosen = null;
        for (CallOption option : options) {
            if (option.type() == CallType.KAN && isValuable(table, seat, option.target())) {
                chosen = option;
                break;
            }
            if (option.type() == CallType.PON && isValuable(table, seat, option.target())) {
                chosen = option;
                break;
            }
            if (option.type() == CallType.CHI && shouldChi(seat, option)) {
                chosen = option;
            }
        }
        table.declareCall(seat.index(), chosen != null ? chosen : CallOption.of(CallType.PASS));
    }

    private static boolean isValuable(MahjongTable table, Seat seat, Tile tile) {
        if (tile.isDragon()) {
            return true;
        }
        if (tile.isWind()) {
            Wind wind = Wind.fromTileId(tile.id());
            return wind == seat.wind() || wind == table.roundWind();
        }
        return false;
    }

    private static boolean shouldChi(Seat seat, CallOption option) {
        if (seat.isClosed()) {
            return false;
        }
        List<Tile> hand = new ArrayList<>(seat.hand());
        for (Tile tile : option.handTiles()) {
            hand.remove(tile);
        }
        for (Tile tile : hand) {
            if (tile.isTerminalOrHonor()) {
                return false;
            }
        }
        return true;
    }

    /** 從候選牌中挑一張最不影響進張的牌打出。 */
    public static Tile bestDiscard(Seat seat, List<Tile> candidates) {
        List<Tile> full = seat.fullHand();
        int meldCount = seat.melds().size();
        List<Tile> distinct = new ArrayList<>();
        boolean[] seen = new boolean[Tile.KINDS];
        for (Tile tile : candidates) {
            if (!seen[tile.id()]) {
                seen[tile.id()] = true;
                distinct.add(tile);
            }
        }
        if (distinct.isEmpty()) {
            return full.getLast();
        }

        int bestShanten = Integer.MAX_VALUE;
        List<Tile> shortlist = new ArrayList<>();
        for (Tile candidate : distinct) {
            List<Tile> remaining = new ArrayList<>(full);
            remaining.remove(candidate);
            int shanten = ShantenCalculator.shanten(Tile.toCounts(remaining), meldCount);
            if (shanten < bestShanten) {
                bestShanten = shanten;
                shortlist.clear();
            }
            if (shanten == bestShanten) {
                shortlist.add(candidate);
            }
        }

        Tile best = shortlist.getFirst();
        int bestScore = Integer.MIN_VALUE;
        for (Tile candidate : shortlist) {
            List<Tile> remaining = new ArrayList<>(full);
            remaining.remove(candidate);
            int score = acceptance(remaining, meldCount, bestShanten) * 10;
            if (candidate.isTerminalOrHonor()) {
                score += 3;
            }
            if (candidate.isRed()) {
                score -= 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static int acceptance(List<Tile> hand, int meldCount, int shanten) {
        int[] counts = Tile.toCounts(hand);
        int total = 0;
        for (int id = 0; id < Tile.KINDS; id++) {
            if (counts[id] >= 4) {
                continue;
            }
            counts[id]++;
            if (ShantenCalculator.shanten(counts, meldCount) < shanten) {
                total += 4 - (counts[id] - 1);
            }
            counts[id]--;
        }
        return total;
    }
}
