package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Scores a finished hand by trying every way it can be read and keeping the
 * most valuable one, which is how japanese mahjong resolves ambiguous shapes.
 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    /**
     * Scores the hand, whose concealed part must already contain the winning
     * tile. Returns {@code null} when the tiles do not finish a hand or the
     * hand has no yaku and so may not be declared.
     */
    public static HandValue evaluate(Hand hand, WinContext context) {
        int[] counts = hand.countsWithDrawn();
        List<Meld> melds = hand.melds();
        if (!WinChecker.isWinningHand(counts, melds.size())) {
            return null;
        }
        boolean closed = hand.isClosed();
        int winningKind = context.winningTile().kind();

        List<Tile> allTiles = hand.allTiles();
        int redDora = (int) allTiles.stream().filter(Tile::red).count();
        int dora = countIndicated(allTiles, context.doraIndicators());
        int uraDora = context.anyRiichi() ? countIndicated(allTiles, context.uraIndicators()) : 0;

        List<Interpretation> interpretations = new ArrayList<>(
                Interpretation.standard(counts, melds, winningKind, !context.tsumo()));
        if (melds.isEmpty()) {
            if (WinChecker.isSevenPairs(counts)) {
                interpretations.add(Interpretation.sevenPairs(counts, winningKind));
            }
            if (WinChecker.isThirteenOrphans(counts)) {
                interpretations.add(Interpretation.forThirteenOrphans());
            }
        }
        if (interpretations.isEmpty()) {
            return null;
        }

        HandValue best = null;
        for (Interpretation interpretation : interpretations) {
            HandValue value = score(interpretation, context, closed, counts, dora, uraDora, redDora);
            if (value != null && (best == null || isBetter(value, best))) {
                best = value;
            }
        }
        return best;
    }

    /** True when the tiles finish a hand and carry at least one yaku. */
    public static boolean canWin(Hand hand, WinContext context) {
        return evaluate(hand, context) != null;
    }

    private static HandValue score(Interpretation interpretation, WinContext context, boolean closed,
                                   int[] counts, int dora, int uraDora, int redDora) {
        List<Yaku> yakuman = YakuEvaluator.yakuman(interpretation, context, closed, counts);
        if (!yakuman.isEmpty()) {
            List<ScoredYaku> scored = yakuman.stream()
                    .map(yaku -> new ScoredYaku(yaku, 0))
                    .toList();
            int multiplier = yakuman.stream().mapToInt(Yaku::yakumanMultiplier).sum();
            int base = ScoreCalculator.YAKUMAN_BASE * multiplier;
            Payment payment = ScoreCalculator.payment(base, context.isDealer(), context.tsumo(),
                    context.honba(), context.riichiSticks());
            return new HandValue(scored, 0, 0, 0, 0, 0, base, "役滿", payment);
        }

        Map<Yaku, Integer> yaku = YakuEvaluator.yaku(interpretation, context, closed);
        if (yaku.isEmpty()) {
            return null;
        }
        List<ScoredYaku> scored = new ArrayList<>();
        int han = 0;
        for (Yaku candidate : Yaku.values()) {
            Integer value = yaku.get(candidate);
            if (value != null) {
                scored.add(new ScoredYaku(candidate, value));
                han += value;
            }
        }
        han += dora + uraDora + redDora;
        int fu = YakuEvaluator.fu(interpretation, context, closed, yaku.containsKey(Yaku.PINFU));
        int base = ScoreCalculator.basePoints(han, fu);
        Payment payment = ScoreCalculator.payment(base, context.isDealer(), context.tsumo(),
                context.honba(), context.riichiSticks());
        return new HandValue(scored, han, fu, dora, uraDora, redDora, base,
                ScoreCalculator.limitName(han, fu), payment);
    }

    private static boolean isBetter(HandValue candidate, HandValue current) {
        return Comparator.comparingInt(HandValue::basePoints)
                .thenComparingInt(HandValue::han)
                .thenComparingInt(HandValue::fu)
                .compare(candidate, current) > 0;
    }

    private static int countIndicated(List<Tile> tiles, List<Tile> indicators) {
        int count = 0;
        for (Tile indicator : indicators) {
            int dora = Tiles.doraFromIndicator(indicator.kind());
            for (Tile tile : tiles) {
                if (tile.kind() == dora) {
                    count++;
                }
            }
        }
        return count;
    }
}
