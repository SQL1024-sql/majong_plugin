package com.majong.riichi.taiwan;

import com.majong.riichi.core.Flower;
import com.majong.riichi.core.Group;
import com.majong.riichi.core.GroupType;
import com.majong.riichi.core.Hand;
import com.majong.riichi.core.Meld;
import com.majong.riichi.core.Suit;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.core.WinChecker;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scores a finished taiwanese hand: five sets and a pair, counted in tai.
 *
 * <p>As in the japanese rules a hand that reads several ways is scored every
 * way and the best reading wins.
 */
public final class TaiwanScorer {

    private TaiwanScorer() {
    }

    /**
     * Scores the hand, whose concealed part must already contain the winning
     * tile. Returns {@code null} when the tiles do not finish a hand.
     */
    public static TaiwanValue score(Hand hand, TaiwanContext context, TaiwanRules rules) {
        int[] counts = hand.countsWithDrawn();
        List<Meld> melds = hand.melds();
        if (!WinChecker.isWinningHand(counts, melds.size(), WinChecker.TAIWANESE_SETS)) {
            return null;
        }
        List<List<Group>> decompositions = WinChecker.standardDecompositions(
                counts, melds.size(), WinChecker.TAIWANESE_SETS);
        if (decompositions.isEmpty()) {
            return null;
        }

        TaiwanValue best = null;
        int winningKind = context.winningTile().kind();
        for (List<Group> concealed : decompositions) {
            for (int index = 0; index < concealed.size(); index++) {
                if (!concealed.get(index).contains(winningKind)) {
                    continue;
                }
                TaiwanValue value = evaluate(concealed, index, melds, hand, context, rules);
                if (best == null || value.tai() > best.tai()) {
                    best = value;
                }
            }
        }
        return best;
    }

    private static TaiwanValue evaluate(List<Group> concealedGroups, int winningIndex,
                                        List<Meld> melds, Hand hand, TaiwanContext context,
                                        TaiwanRules rules) {
        List<Group> groups = new ArrayList<>(concealedGroups);
        Group winning = groups.get(winningIndex);
        boolean ron = !context.tsumo();
        if (ron && winning.type() == GroupType.TRIPLET) {
            // A triplet finished by somebody's discard is not a concealed one.
            groups.set(winningIndex, Group.triplet(winning.kind(), false));
        }
        for (Meld meld : melds) {
            groups.add(Group.fromMeld(meld));
        }

        Map<Tai, Integer> found = new EnumMap<>(Tai.class);
        boolean closed = hand.isClosed();
        List<Group> sets = groups.stream().filter(Group::isSet).toList();
        List<Group> runs = sets.stream().filter(group -> group.type() == GroupType.RUN).toList();
        List<Group> triplets = sets.stream().filter(Group::isTripletLike).toList();
        Group pair = groups.stream().filter(group -> group.type() == GroupType.PAIR)
                .findFirst().orElse(null);

        if (closed) {
            add(found, Tai.MENQING, rules);
        }
        if (context.tsumo()) {
            add(found, Tai.ZIMO, rules);
            if (closed) {
                add(found, Tai.MENQING_ZIMO, rules);
            }
        }
        if (isPinghu(groups, winning, pair, closed, context)) {
            add(found, Tai.PINGHU, rules);
        }
        if (allTiles(groups, Tiles::isSimple)) {
            add(found, Tai.DUANYAOJIU, rules);
        }
        if (melds.size() == WinChecker.TAIWANESE_SETS && ron) {
            add(found, Tai.QUANQIUREN, rules);
        }
        if (isSingleWait(winning, context.winningTile().kind())) {
            add(found, Tai.DANDIAO, rules);
        }

        for (Group triplet : triplets) {
            int kind = triplet.kind();
            if (kind == Tiles.RED_DRAGON) {
                add(found, Tai.YAKUHAI_ZHONG, rules);
            } else if (kind == Tiles.GREEN) {
                add(found, Tai.YAKUHAI_FA, rules);
            } else if (kind == Tiles.WHITE) {
                add(found, Tai.YAKUHAI_BAI, rules);
            }
            if (kind == context.seatWind()) {
                add(found, Tai.SEAT_WIND, rules);
            }
            if (kind == context.roundWind()) {
                add(found, Tai.ROUND_WIND, rules);
            }
        }

        long concealedTriplets = triplets.stream().filter(Group::concealed).count();
        if (concealedTriplets >= 5) {
            add(found, Tai.WUANKE, rules);
        } else if (concealedTriplets == 4) {
            add(found, Tai.SIANKE, rules);
        } else if (concealedTriplets == 3) {
            add(found, Tai.SANANKE, rules);
        }
        if (runs.isEmpty() && !sets.isEmpty()) {
            add(found, Tai.PENGPENGHU, rules);
        }

        Suit suit = singleSuit(groups);
        boolean honours = !allTiles(groups, kind -> !Tiles.isHonor(kind));
        if (allTiles(groups, Tiles::isHonor)) {
            add(found, Tai.ZIYISE, rules);
        } else if (suit != null) {
            add(found, honours ? Tai.HUNYISE : Tai.QINGYISE, rules);
        }
        if (allTiles(groups, Tiles::isTerminalOrHonor)) {
            add(found, honours ? Tai.HUNLAOTOU : Tai.QINGLAOTOU, rules);
        }

        long dragons = triplets.stream().filter(group -> Tiles.isDragon(group.kind())).count();
        if (dragons == 3) {
            add(found, Tai.DASANYUAN, rules);
        } else if (dragons == 2 && pair != null && Tiles.isDragon(pair.kind())) {
            add(found, Tai.XIAOSANYUAN, rules);
        }
        long winds = triplets.stream().filter(group -> Tiles.isWind(group.kind())).count();
        if (winds == 4) {
            add(found, Tai.DASIXI, rules);
        } else if (winds == 3 && pair != null && Tiles.isWind(pair.kind())) {
            add(found, Tai.XIAOSIXI, rules);
        }

        if (context.afterKan()) {
            add(found, Tai.GANGSHANG, rules);
        }
        if (context.robbingKan()) {
            add(found, Tai.QIANGGANG, rules);
        }
        if (context.lastDraw()) {
            add(found, Tai.HAIDI, rules);
        }
        if (context.lastDiscard()) {
            add(found, Tai.HEDI, rules);
        }
        if (context.firstTurn()) {
            if (context.isDealer() && context.tsumo()) {
                add(found, Tai.TIANHU, rules);
            } else if (context.tsumo()) {
                add(found, Tai.DIHU, rules);
            } else {
                add(found, Tai.RENHU, rules);
            }
        }

        addFlowers(found, context, rules);

        if (context.isDealer()) {
            add(found, Tai.DEALER, rules);
            if (context.dealerStreak() > 0) {
                // Every hand the dealer keeps their seat adds another helping.
                found.merge(Tai.DEALER_STREAK,
                        rules.valueOf(Tai.DEALER_STREAK) * context.dealerStreak(), Integer::sum);
            }
        }

        List<TaiwanValue.ScoredTai> scored = new ArrayList<>();
        int tai = 0;
        for (Tai pattern : Tai.values()) {
            Integer value = found.get(pattern);
            if (value != null && value > 0) {
                scored.add(new TaiwanValue.ScoredTai(pattern, value));
                tai += value;
            }
        }

        int perPlayer = rules.payment(tai);
        if (context.tsumo()) {
            return new TaiwanValue(scored, tai, perPlayer, perPlayer * 3, 0);
        }
        return new TaiwanValue(scored, tai, perPlayer, perPlayer, perPlayer);
    }

    private static void addFlowers(Map<Tai, Integer> found, TaiwanContext context,
                                   TaiwanRules rules) {
        int matching = 0;
        int seasons = 0;
        int plants = 0;
        for (Flower flower : context.flowers()) {
            if (flower.matches(context.seatIndex())) {
                matching++;
            }
            if (flower.isSeason()) {
                seasons++;
            } else {
                plants++;
            }
        }
        if (matching > 0) {
            found.merge(Tai.FLOWER, rules.valueOf(Tai.FLOWER) * matching, Integer::sum);
        }
        // Holding a whole set of four seasons or four plants is worth more again.
        int sets = (seasons == 4 ? 1 : 0) + (plants == 4 ? 1 : 0);
        if (sets > 0) {
            found.merge(Tai.FLOWER_GANG, rules.valueOf(Tai.FLOWER_GANG) * sets, Integer::sum);
        }
    }

    private static boolean isPinghu(List<Group> groups, Group winning, Group pair, boolean closed,
                                    TaiwanContext context) {
        if (!closed || pair == null || winning.type() != GroupType.RUN) {
            return false;
        }
        if (groups.stream().filter(Group::isSet).anyMatch(g -> g.type() != GroupType.RUN)) {
            return false;
        }
        int pairKind = pair.kind();
        if (Tiles.isDragon(pairKind) || pairKind == context.seatWind()
                || pairKind == context.roundWind()) {
            return false;
        }
        // A two sided wait only; an edge or closed wait does not count.
        return !isSingleWait(winning, context.winningTile().kind());
    }

    /**
     * True for a lone wait: the pair, a gap, or an edge. Waiting on either of
     * two pairs is not counted, which is the usual reading.
     */
    private static boolean isSingleWait(Group winning, int winningKind) {
        return switch (winning.type()) {
            case TRIPLET, KAN -> false;
            case PAIR -> true;
            case RUN -> {
                if (winningKind == winning.kind() + 1) {
                    yield true;
                }
                boolean lowEdge = Tiles.rankOf(winning.kind()) == 1
                        && winningKind == winning.kind() + 2;
                boolean highEdge = Tiles.rankOf(winning.kind()) == 7
                        && winningKind == winning.kind();
                yield lowEdge || highEdge;
            }
        };
    }

    private static void add(Map<Tai, Integer> found, Tai pattern, TaiwanRules rules) {
        found.merge(pattern, rules.valueOf(pattern), Integer::sum);
    }

    private static boolean allTiles(List<Group> groups, java.util.function.IntPredicate test) {
        for (Group group : groups) {
            for (int kind : group.kinds()) {
                if (!test.test(kind)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Suit singleSuit(List<Group> groups) {
        Suit suit = null;
        for (Group group : groups) {
            for (int kind : group.kinds()) {
                Suit current = Tiles.suitOf(kind);
                if (current == Suit.HONOR) {
                    continue;
                }
                if (suit == null) {
                    suit = current;
                } else if (suit != current) {
                    return null;
                }
            }
        }
        return suit;
    }
}
