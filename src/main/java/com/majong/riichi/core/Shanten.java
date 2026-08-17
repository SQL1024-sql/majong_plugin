package com.majong.riichi.core;

/**
 * How many tile swaps a hand still needs before it is ready.
 *
 * <p>A finished hand is {@code -1}, a ready hand {@code 0}, and anything higher
 * counts the exchanges left to go.
 */
public final class Shanten {

    private Shanten() {
    }

    /** The best of the ordinary, seven pairs and thirteen orphans readings. */
    public static int calculate(int[] counts, int meldCount) {
        return calculate(counts, meldCount, WinChecker.JAPANESE_SETS);
    }

    /**
     * Shanten for a hand of the given size. Seven pairs and thirteen orphans
     * are japanese shapes, so they only apply to a four set hand.
     */
    public static int calculate(int[] counts, int meldCount, int totalSets) {
        int best = standard(counts, meldCount, totalSets);
        if (meldCount == 0 && totalSets == WinChecker.JAPANESE_SETS) {
            best = Math.min(best, sevenPairs(counts));
            best = Math.min(best, thirteenOrphans(counts));
        }
        return best;
    }

    /** Shanten counting only ordinary sets and a pair. */
    public static int standard(int[] counts, int meldCount) {
        return standard(counts, meldCount, WinChecker.JAPANESE_SETS);
    }

    public static int standard(int[] counts, int meldCount, int totalSets) {
        Search search = new Search(counts.clone(), totalSets);
        search.run(0, meldCount, 0, false);
        return search.best;
    }

    public static int sevenPairs(int[] counts) {
        int pairs = 0;
        int kinds = 0;
        for (int count : counts) {
            if (count > 0) {
                kinds++;
            }
            if (count >= 2) {
                pairs++;
            }
        }
        return 6 - pairs + Math.max(0, 7 - kinds);
    }

    public static int thirteenOrphans(int[] counts) {
        int kinds = 0;
        boolean pair = false;
        for (int kind : WinChecker.ORPHANS) {
            if (counts[kind] > 0) {
                kinds++;
            }
            if (counts[kind] >= 2) {
                pair = true;
            }
        }
        return 13 - kinds - (pair ? 1 : 0);
    }

    /**
     * The tile kinds that would bring the hand one step closer to ready.
     */
    public static java.util.Set<Integer> improvingTiles(int[] counts, int meldCount) {
        return improvingTiles(counts, meldCount, WinChecker.JAPANESE_SETS);
    }

    public static java.util.Set<Integer> improvingTiles(int[] counts, int meldCount,
                                                        int totalSets) {
        java.util.Set<Integer> useful = new java.util.LinkedHashSet<>();
        int current = calculate(counts, meldCount, totalSets);
        int[] working = counts.clone();
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            if (working[kind] >= 4) {
                continue;
            }
            working[kind]++;
            if (calculate(working, meldCount, totalSets) < current) {
                useful.add(kind);
            }
            working[kind]--;
        }
        return useful;
    }

    /**
     * Walks the hand kind by kind, taking complete sets first and then partial
     * ones, and keeps the cheapest arrangement it finds.
     */
    private static final class Search {
        private final int[] counts;
        private final int totalSets;
        /** A hand needs this many blocks: the sets plus the pair. */
        private final int maxBlocks;
        private int best;

        Search(int[] counts, int totalSets) {
            this.counts = counts;
            this.totalSets = totalSets;
            this.maxBlocks = totalSets + 1;
            this.best = 2 * totalSets;
        }

        void run(int index, int sets, int partials, boolean hasPair) {
            if (index >= Tiles.KINDS) {
                score(sets, partials, hasPair);
                return;
            }
            if (counts[index] == 0) {
                run(index + 1, sets, partials, hasPair);
                return;
            }
            int blocks = sets + partials;

            if (counts[index] >= 3 && blocks < maxBlocks) {
                counts[index] -= 3;
                run(index, sets + 1, partials, hasPair);
                counts[index] += 3;
            }
            if (Tiles.canStartRun(index) && counts[index + 1] > 0 && counts[index + 2] > 0 && blocks < maxBlocks) {
                counts[index]--;
                counts[index + 1]--;
                counts[index + 2]--;
                run(index, sets + 1, partials, hasPair);
                counts[index]++;
                counts[index + 1]++;
                counts[index + 2]++;
            }
            if (counts[index] >= 2 && blocks < maxBlocks) {
                counts[index] -= 2;
                run(index, sets, partials + 1, true);
                counts[index] += 2;
            }
            if (!Tiles.isHonor(index) && blocks < maxBlocks) {
                // An open ended or closed pair of neighbours still needs one tile.
                if (Tiles.rankOf(index) <= 8 && counts[index + 1] > 0) {
                    counts[index]--;
                    counts[index + 1]--;
                    run(index, sets, partials + 1, hasPair);
                    counts[index]++;
                    counts[index + 1]++;
                }
                if (Tiles.rankOf(index) <= 7 && counts[index + 2] > 0) {
                    counts[index]--;
                    counts[index + 2]--;
                    run(index, sets, partials + 1, hasPair);
                    counts[index]++;
                    counts[index + 2]++;
                }
            }
            // Leave this tile floating and move on.
            counts[index]--;
            run(index, sets, partials, hasPair);
            counts[index]++;
        }

        private void score(int sets, int partials, boolean hasPair) {
            int value = 2 * totalSets - 2 * sets - partials;
            if (sets + partials == maxBlocks && !hasPair) {
                // Five blocks with no pair leaves the hand without a head.
                value++;
            }
            if (value < best) {
                best = value;
            }
        }
    }
}
