package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the yaku in one reading of a finished hand and works out its fu.
 */
public final class YakuEvaluator {

    private static final int[] GREEN_KINDS = {
        Tiles.kindOf(Suit.SOU, 2), Tiles.kindOf(Suit.SOU, 3), Tiles.kindOf(Suit.SOU, 4),
        Tiles.kindOf(Suit.SOU, 6), Tiles.kindOf(Suit.SOU, 8), Tiles.GREEN
    };

    private static final int[] CHUUREN_SHAPE = {3, 1, 1, 1, 1, 1, 1, 1, 3};

    private YakuEvaluator() {
    }

    /**
     * Limit hands present in this reading. When the result is non-empty the
     * ordinary yaku are not scored at all.
     */
    public static List<Yaku> yakuman(Interpretation interpretation, WinContext context,
                                     boolean closed, int[] concealedCounts) {
        List<Yaku> found = new ArrayList<>();
        if (context.tenhou()) {
            found.add(Yaku.TENHOU);
        }
        if (context.chiihou()) {
            found.add(Yaku.CHIIHOU);
        }
        if (interpretation.thirteenOrphans()) {
            found.add(concealedCounts[context.winningTile().kind()] == 2
                    ? Yaku.KOKUSHI_JUUSANMEN : Yaku.KOKUSHI);
            return found;
        }
        if (interpretation.sevenPairs()) {
            addAllHonoursOrTerminals(interpretation, found);
            return found;
        }

        List<Group> sets = interpretation.sets();
        long concealedTriplets = sets.stream()
                .filter(group -> group.isTripletLike() && group.concealed())
                .count();
        if (concealedTriplets == 4) {
            Group winning = winningGroup(interpretation);
            found.add(winning != null && winning.type() == GroupType.PAIR
                    ? Yaku.SUUANKOU_TANKI : Yaku.SUUANKOU);
        }
        if (sets.stream().filter(group -> group.type() == GroupType.KAN).count() == 4) {
            found.add(Yaku.SUUKANTSU);
        }
        long dragonSets = sets.stream()
                .filter(group -> group.isTripletLike() && Tiles.isDragon(group.kind()))
                .count();
        if (dragonSets == 3) {
            found.add(Yaku.DAISANGEN);
        }
        long windSets = sets.stream()
                .filter(group -> group.isTripletLike() && Tiles.isWind(group.kind()))
                .count();
        Group pair = interpretation.pair();
        if (windSets == 4) {
            found.add(Yaku.DAISUUSHII);
        } else if (windSets == 3 && pair != null && Tiles.isWind(pair.kind())) {
            found.add(Yaku.SHOUSUUSHII);
        }
        addAllHonoursOrTerminals(interpretation, found);
        if (allKindsIn(interpretation, GREEN_KINDS)) {
            found.add(Yaku.RYUUIISOU);
        }
        if (closed) {
            Yaku chuuren = chuuren(concealedCounts, context.winningTile().kind());
            if (chuuren != null) {
                found.add(chuuren);
            }
        }
        return found;
    }

    /** The ordinary yaku present in this reading, with the han each is worth. */
    public static Map<Yaku, Integer> yaku(Interpretation interpretation, WinContext context, boolean closed) {
        Map<Yaku, Integer> found = new HashMap<>();
        EnumSet<Yaku> flags = EnumSet.noneOf(Yaku.class);

        if (context.doubleRiichi()) {
            flags.add(Yaku.DOUBLE_RIICHI);
        } else if (context.riichi()) {
            flags.add(Yaku.RIICHI);
        }
        if (context.ippatsu()) {
            flags.add(Yaku.IPPATSU);
        }
        if (closed && context.tsumo()) {
            flags.add(Yaku.MENZEN_TSUMO);
        }
        if (context.rinshan()) {
            flags.add(Yaku.RINSHAN);
        }
        if (context.chankan()) {
            flags.add(Yaku.CHANKAN);
        }
        if (context.haitei()) {
            flags.add(Yaku.HAITEI);
        }
        if (context.houtei()) {
            flags.add(Yaku.HOUTEI);
        }

        if (interpretation.sevenPairs()) {
            flags.add(Yaku.CHIITOITSU);
        }

        List<Group> groups = interpretation.groups();
        List<Group> sets = interpretation.sets();
        List<Group> runs = sets.stream().filter(group -> group.type() == GroupType.RUN).toList();
        List<Group> triplets = sets.stream().filter(Group::isTripletLike).toList();
        Group pair = interpretation.pair();

        if ((closed || context.openTanyao()) && allTilesMatch(interpretation, Tiles::isSimple)) {
            flags.add(Yaku.TANYAO);
        }
        if (allTilesMatch(interpretation, Tiles::isTerminalOrHonor)) {
            flags.add(Yaku.HONROUTOU);
        }

        for (Group triplet : triplets) {
            int kind = triplet.kind();
            if (kind == Tiles.WHITE) {
                flags.add(Yaku.YAKUHAI_HAKU);
            } else if (kind == Tiles.GREEN) {
                flags.add(Yaku.YAKUHAI_HATSU);
            } else if (kind == Tiles.RED_DRAGON) {
                flags.add(Yaku.YAKUHAI_CHUN);
            }
            if (kind == context.seatWind()) {
                flags.add(Yaku.YAKUHAI_SEAT);
            }
            if (kind == context.roundWind()) {
                flags.add(Yaku.YAKUHAI_ROUND);
            }
        }

        if (!interpretation.isSpecial()) {
            if (isPinfu(interpretation, context, closed)) {
                flags.add(Yaku.PINFU);
            }
            if (runs.isEmpty() && !sets.isEmpty()) {
                flags.add(Yaku.TOITOI);
            }
            long concealedTriplets = triplets.stream().filter(Group::concealed).count();
            if (concealedTriplets == 3) {
                flags.add(Yaku.SANANKOU);
            }
            if (triplets.stream().filter(group -> group.type() == GroupType.KAN).count() == 3) {
                flags.add(Yaku.SANKANTSU);
            }
            long dragonSets = triplets.stream().filter(group -> Tiles.isDragon(group.kind())).count();
            if (dragonSets == 2 && pair != null && Tiles.isDragon(pair.kind())) {
                flags.add(Yaku.SHOUSANGEN);
            }
            if (hasSanshokuDoujun(runs)) {
                flags.add(Yaku.SANSHOKU_DOUJUN);
            }
            if (hasSanshokuDoukou(triplets)) {
                flags.add(Yaku.SANSHOKU_DOUKOU);
            }
            if (hasIttsu(runs)) {
                flags.add(Yaku.ITTSU);
            }
            if (closed) {
                int identicalRunPairs = countIdenticalRunPairs(runs);
                if (identicalRunPairs >= 2) {
                    flags.add(Yaku.RYANPEIKOU);
                } else if (identicalRunPairs == 1) {
                    flags.add(Yaku.IIPEIKO);
                }
            }
            if (!runs.isEmpty() && groups.stream().allMatch(Group::hasTerminalOrHonor)) {
                flags.add(containsHonor(interpretation) ? Yaku.CHANTA : Yaku.JUNCHAN);
            }
        }

        Suit onlySuit = singleNumberedSuit(interpretation);
        if (onlySuit != null) {
            flags.add(containsHonor(interpretation) ? Yaku.HONITSU : Yaku.CHINITSU);
        }

        // Honroutou already covers every tile being a terminal or honor, so the
        // weaker chanta line would only double count the same shape.
        if (flags.contains(Yaku.HONROUTOU)) {
            flags.remove(Yaku.CHANTA);
            flags.remove(Yaku.JUNCHAN);
        }

        for (Yaku yaku : flags) {
            int han = yaku.han(closed);
            if (han > 0) {
                found.put(yaku, han);
            }
        }
        return found;
    }

    /** Fu for this reading; seven pairs is always the flat 25. */
    public static int fu(Interpretation interpretation, WinContext context, boolean closed, boolean pinfu) {
        if (interpretation.sevenPairs()) {
            return 25;
        }
        if (interpretation.thirteenOrphans()) {
            return 25;
        }
        int fu = 20;
        boolean ron = !context.tsumo();
        if (closed && ron) {
            fu += 10;
        }
        if (context.tsumo() && !pinfu) {
            fu += 2;
        }
        if (!pinfu) {
            fu += interpretation.waitType().fu();
        }
        Group pair = interpretation.pair();
        if (pair != null) {
            int kind = pair.kind();
            if (Tiles.isDragon(kind) || kind == context.seatWind() || kind == context.roundWind()) {
                fu += 2;
            }
        }
        for (Group group : interpretation.sets()) {
            if (!group.isTripletLike()) {
                continue;
            }
            boolean honorOrTerminal = Tiles.isTerminalOrHonor(group.kind());
            int base = group.type() == GroupType.KAN ? 8 : 2;
            if (group.concealed()) {
                base *= 2;
            }
            if (honorOrTerminal) {
                base *= 2;
            }
            fu += base;
        }
        fu = (fu + 9) / 10 * 10;
        if (!closed && fu == 20) {
            fu = 30;
        }
        return fu;
    }

    static boolean isPinfu(Interpretation interpretation, WinContext context, boolean closed) {
        if (!closed || interpretation.isSpecial()) {
            return false;
        }
        if (interpretation.waitType() != WaitType.RYANMEN) {
            return false;
        }
        List<Group> sets = interpretation.sets();
        if (sets.size() != 4 || sets.stream().anyMatch(group -> group.type() != GroupType.RUN)) {
            return false;
        }
        Group pair = interpretation.pair();
        if (pair == null) {
            return false;
        }
        int kind = pair.kind();
        return !Tiles.isDragon(kind) && kind != context.seatWind() && kind != context.roundWind();
    }

    private static void addAllHonoursOrTerminals(Interpretation interpretation, List<Yaku> found) {
        if (allTilesMatch(interpretation, Tiles::isHonor)) {
            found.add(Yaku.TSUUIISOU);
        } else if (allTilesMatch(interpretation, Tiles::isTerminal)) {
            found.add(Yaku.CHINROUTOU);
        }
    }

    private static Group winningGroup(Interpretation interpretation) {
        int index = interpretation.winningGroup();
        if (index < 0 || index >= interpretation.groups().size()) {
            return null;
        }
        return interpretation.groups().get(index);
    }

    private static Yaku chuuren(int[] counts, int winningKind) {
        Suit suit = null;
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            if (counts[kind] == 0) {
                continue;
            }
            Suit current = Tiles.suitOf(kind);
            if (current == Suit.HONOR) {
                return null;
            }
            if (suit == null) {
                suit = current;
            } else if (suit != current) {
                return null;
            }
        }
        if (suit == null) {
            return null;
        }
        int base = Tiles.kindOf(suit, 1);
        int extra = -1;
        for (int rank = 0; rank < 9; rank++) {
            int diff = counts[base + rank] - CHUUREN_SHAPE[rank];
            if (diff == 1 && extra < 0) {
                extra = base + rank;
            } else if (diff != 0) {
                return null;
            }
        }
        if (extra < 0) {
            return null;
        }
        // Taking the winning tile back out leaves the pure nine sided wait only
        // when that tile was the surplus one.
        return extra == winningKind ? Yaku.CHUUREN_JUNSEI : Yaku.CHUUREN;
    }

    private static boolean allTilesMatch(Interpretation interpretation, java.util.function.IntPredicate test) {
        for (Group group : interpretation.groups()) {
            for (int kind : group.kinds()) {
                if (!test.test(kind)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean allKindsIn(Interpretation interpretation, int[] allowed) {
        return allTilesMatch(interpretation, kind -> {
            for (int candidate : allowed) {
                if (candidate == kind) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean containsHonor(Interpretation interpretation) {
        return !allTilesMatch(interpretation, kind -> !Tiles.isHonor(kind));
    }

    private static Suit singleNumberedSuit(Interpretation interpretation) {
        Suit suit = null;
        for (Group group : interpretation.groups()) {
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

    private static boolean hasSanshokuDoujun(List<Group> runs) {
        for (Group run : runs) {
            if (Tiles.isHonor(run.kind())) {
                continue;
            }
            int rank = Tiles.rankOf(run.kind());
            boolean man = false;
            boolean pin = false;
            boolean sou = false;
            for (Group other : runs) {
                if (Tiles.rankOf(other.kind()) != rank) {
                    continue;
                }
                switch (Tiles.suitOf(other.kind())) {
                    case MAN -> man = true;
                    case PIN -> pin = true;
                    case SOU -> sou = true;
                    default -> {
                    }
                }
            }
            if (man && pin && sou) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSanshokuDoukou(List<Group> triplets) {
        for (Group triplet : triplets) {
            if (Tiles.isHonor(triplet.kind())) {
                continue;
            }
            int rank = Tiles.rankOf(triplet.kind());
            boolean man = false;
            boolean pin = false;
            boolean sou = false;
            for (Group other : triplets) {
                if (Tiles.isHonor(other.kind()) || Tiles.rankOf(other.kind()) != rank) {
                    continue;
                }
                switch (Tiles.suitOf(other.kind())) {
                    case MAN -> man = true;
                    case PIN -> pin = true;
                    case SOU -> sou = true;
                    default -> {
                    }
                }
            }
            if (man && pin && sou) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIttsu(List<Group> runs) {
        for (Suit suit : new Suit[]{Suit.MAN, Suit.PIN, Suit.SOU}) {
            boolean low = false;
            boolean mid = false;
            boolean high = false;
            for (Group run : runs) {
                if (Tiles.suitOf(run.kind()) != suit) {
                    continue;
                }
                switch (Tiles.rankOf(run.kind())) {
                    case 1 -> low = true;
                    case 4 -> mid = true;
                    case 7 -> high = true;
                    default -> {
                    }
                }
            }
            if (low && mid && high) {
                return true;
            }
        }
        return false;
    }

    private static int countIdenticalRunPairs(List<Group> runs) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Group run : runs) {
            counts.merge(run.kind(), 1, Integer::sum);
        }
        int pairs = 0;
        for (int count : counts.values()) {
            pairs += count / 2;
        }
        return pairs;
    }
}
