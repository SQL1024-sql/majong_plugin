package com.majong.riichi.bot;

import com.majong.riichi.core.Hand;
import com.majong.riichi.core.Shanten;
import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.Action;
import com.majong.riichi.game.RiichiGame;
import java.util.List;

/**
 * A straightforward computer player: it finishes hands when it can, declares
 * riichi once ready, and otherwise throws whichever tile leaves it closest to
 * ready. It claims a discard only when doing so builds a triplet of value tiles.
 */
public final class SimpleBot {

    private SimpleBot() {
    }

    /** Picks one of the actions the engine is offering this seat. */
    public static Action decide(RiichiGame game, int seat) {
        List<Action> options = game.options(seat);
        if (options.isEmpty()) {
            return null;
        }
        for (Action action : options) {
            if (action instanceof Action.Tsumo || action instanceof Action.Ron) {
                return action;
            }
        }
        if (game.phase() == RiichiGame.Phase.CALL) {
            return decideCall(game, seat, options);
        }
        for (Action action : options) {
            if (action instanceof Action.NineTerminals) {
                return action;
            }
        }
        return decideDiscard(game, seat, options);
    }

    private static Action decideCall(RiichiGame game, int seat, List<Action> options) {
        Tile discarded = game.lastDiscard();
        if (discarded != null && isValuable(game, seat, discarded.kind())) {
            for (Action action : options) {
                if (action instanceof Action.Pon) {
                    return action;
                }
            }
        }
        // Anything else would open the hand for no yaku worth having.
        return options.stream()
                .filter(action -> action instanceof Action.Pass)
                .findFirst()
                .orElse(options.getFirst());
    }

    private static boolean isValuable(RiichiGame game, int seat, int kind) {
        if (game.seat(seat).hand().count(kind) < 2) {
            return false;
        }
        return Tiles.isDragon(kind) || kind == game.seatWind(seat) || kind == game.roundWind();
    }

    private static Action decideDiscard(RiichiGame game, int seat, List<Action> options) {
        Hand hand = game.seat(seat).hand();
        int melds = hand.melds().size();
        int totalSets = game.variant().totalSets();
        int[] counts = hand.countsWithDrawn();

        Action best = null;
        int bestShanten = Integer.MAX_VALUE;
        int bestUseful = -1;
        boolean bestRiichi = false;
        int bestSafety = Integer.MIN_VALUE;

        for (Action action : options) {
            if (!(action instanceof Action.Discard discard)) {
                continue;
            }
            counts[discard.tile().kind()]--;
            int shanten = Shanten.calculate(counts, melds, totalSets);
            // Early on almost everything helps, so counting useful tiles costs
            // a lot and tells us little; lean on the tile's own value instead.
            int useful = shanten <= 0 || shanten >= 4
                    ? 0 : Shanten.improvingTiles(counts, melds, totalSets).size();
            counts[discard.tile().kind()]++;

            int safety = Tiles.isTerminalOrHonor(discard.tile().kind()) ? 1 : 0;
            if (discard.tile().red()) {
                safety -= 5;
            }
            if (isBetter(shanten, useful, discard.riichi(), safety,
                    bestShanten, bestUseful, bestRiichi, bestSafety) || best == null) {
                best = action;
                bestShanten = shanten;
                bestUseful = useful;
                bestRiichi = discard.riichi();
                bestSafety = safety;
            }
        }
        return best == null ? options.getFirst() : best;
    }

    private static boolean isBetter(int shanten, int useful, boolean riichi, int safety,
                                    int bestShanten, int bestUseful, boolean bestRiichi,
                                    int bestSafety) {
        if (shanten != bestShanten) {
            return shanten < bestShanten;
        }
        if (riichi != bestRiichi) {
            return riichi;
        }
        if (useful != bestUseful) {
            return useful > bestUseful;
        }
        return safety > bestSafety;
    }
}
