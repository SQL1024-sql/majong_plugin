package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a set of tiles forms a finished hand, and enumerates the ways
 * it can be read as four sets and a pair.
 */
public final class WinChecker {

    /** The thirteen terminal and honor kinds that make up thirteen orphans. */
    public static final int[] ORPHANS = {
        Tiles.kindOf(Suit.MAN, 1), Tiles.kindOf(Suit.MAN, 9),
        Tiles.kindOf(Suit.PIN, 1), Tiles.kindOf(Suit.PIN, 9),
        Tiles.kindOf(Suit.SOU, 1), Tiles.kindOf(Suit.SOU, 9),
        Tiles.EAST, Tiles.SOUTH, Tiles.WEST, Tiles.NORTH,
        Tiles.WHITE, Tiles.GREEN, Tiles.RED_DRAGON
    };

    private WinChecker() {
    }

    /**
     * True when the concealed tiles complete the hand, counting the tiles
     * already locked away in {@code meldCount} melds.
     */
    public static boolean isWinningHand(int[] counts, int meldCount) {
        if (Tiles.totalCount(counts) != 3 * (4 - meldCount) + 2) {
            return false;
        }
        if (meldCount == 0 && (isSevenPairs(counts) || isThirteenOrphans(counts))) {
            return true;
        }
        return !standardDecompositions(counts, meldCount).isEmpty();
    }

    public static boolean isSevenPairs(int[] counts) {
        if (Tiles.totalCount(counts) != 14) {
            return false;
        }
        int pairs = 0;
        for (int count : counts) {
            if (count == 0) {
                continue;
            }
            if (count != 2) {
                return false;
            }
            pairs++;
        }
        return pairs == 7;
    }

    public static boolean isThirteenOrphans(int[] counts) {
        if (Tiles.totalCount(counts) != 14) {
            return false;
        }
        int pairs = 0;
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            int count = counts[kind];
            if (Tiles.isTerminalOrHonor(kind)) {
                if (count == 0 || count > 2) {
                    return false;
                }
                if (count == 2) {
                    pairs++;
                }
            } else if (count != 0) {
                return false;
            }
        }
        return pairs == 1;
    }

    /** The kind that is paired in a thirteen orphans hand, or {@code -1}. */
    public static int thirteenOrphansPair(int[] counts) {
        for (int kind : ORPHANS) {
            if (counts[kind] == 2) {
                return kind;
            }
        }
        return -1;
    }

    /**
     * All ways the concealed tiles read as sets plus one pair, given how many
     * melds already sit outside the hand.
     */
    public static List<List<Group>> standardDecompositions(int[] counts, int meldCount) {
        List<List<Group>> results = new ArrayList<>();
        int neededSets = 4 - meldCount;
        if (Tiles.totalCount(counts) != 3 * neededSets + 2) {
            return results;
        }
        int[] working = counts.clone();
        for (int pair = 0; pair < Tiles.KINDS; pair++) {
            if (working[pair] < 2) {
                continue;
            }
            working[pair] -= 2;
            List<Group> current = new ArrayList<>();
            current.add(Group.pair(pair));
            extractSets(working, 0, neededSets, current, results);
            working[pair] += 2;
        }
        return results;
    }

    private static void extractSets(int[] counts, int start, int remainingSets,
                                    List<Group> current, List<List<Group>> results) {
        if (remainingSets == 0) {
            results.add(List.copyOf(current));
            return;
        }
        int index = start;
        while (index < Tiles.KINDS && counts[index] == 0) {
            index++;
        }
        if (index == Tiles.KINDS) {
            return;
        }
        if (counts[index] >= 3) {
            counts[index] -= 3;
            current.add(Group.triplet(index, true));
            extractSets(counts, index, remainingSets - 1, current, results);
            current.removeLast();
            counts[index] += 3;
        }
        if (Tiles.canStartRun(index) && counts[index + 1] > 0 && counts[index + 2] > 0) {
            counts[index]--;
            counts[index + 1]--;
            counts[index + 2]--;
            current.add(Group.run(index, true));
            extractSets(counts, index, remainingSets - 1, current, results);
            current.removeLast();
            counts[index]++;
            counts[index + 1]++;
            counts[index + 2]++;
        }
    }

    /**
     * The tile kinds that would complete the given waiting hand. Empty when the
     * hand is not ready.
     */
    public static Set<Integer> waits(int[] counts, int meldCount) {
        Set<Integer> waits = new LinkedHashSet<>();
        if (Tiles.totalCount(counts) != 3 * (4 - meldCount) + 1) {
            return waits;
        }
        int[] working = counts.clone();
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            if (working[kind] >= 4) {
                continue;
            }
            working[kind]++;
            if (isWinningHand(working, meldCount)) {
                waits.add(kind);
            }
            working[kind]--;
        }
        return waits;
    }

    public static boolean isTenpai(int[] counts, int meldCount) {
        return !waits(counts, meldCount).isEmpty();
    }
}
