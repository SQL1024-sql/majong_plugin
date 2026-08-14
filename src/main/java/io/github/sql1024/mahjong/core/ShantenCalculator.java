package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 向聽數計算。
 *
 * <p>回傳值：-1 表示已和牌、0 表示聽牌、n 表示 n 向聽。
 */
public final class ShantenCalculator {

    private ShantenCalculator() {
    }

    /**
     * 計算向聽數。
     *
     * @param concealed  門前手牌的 34 種計數 (3n+1 或 3n+2 張)
     * @param meldCount  已副露的組數
     */
    public static int shanten(int[] concealed, int meldCount) {
        int best = standardShanten(concealed, meldCount);
        if (meldCount == 0) {
            best = Math.min(best, sevenPairsShanten(concealed));
            best = Math.min(best, thirteenOrphansShanten(concealed));
        }
        return best;
    }

    /** 是否聽牌 (含已和牌的情況)。 */
    public static boolean isTenpai(int[] concealed, int meldCount) {
        return shanten(concealed, meldCount) <= 0;
    }

    /**
     * 列出所有的聽牌張 (可和的牌)。呼叫時手牌應為 3n+1 張。
     */
    public static List<Integer> waits(int[] concealed, List<Meld> melds) {
        List<Integer> waits = new ArrayList<>();
        int[] counts = concealed.clone();
        for (int id = 0; id < Tile.KINDS; id++) {
            if (counts[id] >= 4) {
                continue;
            }
            counts[id]++;
            if (HandParser.isWinningHand(counts, melds)) {
                waits.add(id);
            }
            counts[id]--;
        }
        return waits;
    }

    public static int standardShanten(int[] concealed, int meldCount) {
        int[] counts = concealed.clone();
        Search search = new Search();
        search.run(counts, 0, meldCount, 0, 0);
        return search.best;
    }

    public static int sevenPairsShanten(int[] counts) {
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
        int shanten = 6 - pairs;
        if (kinds < 7) {
            shanten += 7 - kinds;
        }
        return shanten;
    }

    public static int thirteenOrphansShanten(int[] counts) {
        int kinds = 0;
        boolean pair = false;
        for (int orphan : HandParser.ORPHANS) {
            if (counts[orphan] > 0) {
                kinds++;
            }
            if (counts[orphan] >= 2) {
                pair = true;
            }
        }
        return 13 - kinds - (pair ? 1 : 0);
    }

    private static final class Search {
        private int best = Integer.MAX_VALUE;

        private void run(int[] counts, int start, int melds, int partials, int pairs) {
            int i = start;
            while (i < Tile.KINDS && counts[i] == 0) {
                i++;
            }
            evaluate(melds, partials, pairs);
            if (i == Tile.KINDS || melds + partials >= 5) {
                return;
            }

            if (counts[i] >= 3) {
                counts[i] -= 3;
                run(counts, i, melds + 1, partials, pairs);
                counts[i] += 3;
            }
            if (HandParser.canStartSequence(i) && counts[i + 1] > 0 && counts[i + 2] > 0) {
                counts[i]--;
                counts[i + 1]--;
                counts[i + 2]--;
                run(counts, i, melds + 1, partials, pairs);
                counts[i]++;
                counts[i + 1]++;
                counts[i + 2]++;
            }
            if (counts[i] >= 2) {
                counts[i] -= 2;
                run(counts, i, melds, partials + 1, pairs + 1);
                counts[i] += 2;
            }
            if (isNumbered(i) && Tile.of(i).number() <= 8 && counts[i + 1] > 0) {
                counts[i]--;
                counts[i + 1]--;
                run(counts, i, melds, partials + 1, pairs);
                counts[i]++;
                counts[i + 1]++;
            }
            if (HandParser.canStartSequence(i) && counts[i + 2] > 0) {
                counts[i]--;
                counts[i + 2]--;
                run(counts, i, melds, partials + 1, pairs);
                counts[i]++;
                counts[i + 2]++;
            }

            counts[i]--;
            run(counts, i, melds, partials, pairs);
            counts[i]++;
        }

        private void evaluate(int melds, int partials, int pairs) {
            if (melds > 4 || melds + partials > 5) {
                return;
            }
            int value = 8 - 2 * melds - partials;
            if (melds + partials == 5 && pairs == 0) {
                value++;
            }
            best = Math.min(best, value);
        }

        private static boolean isNumbered(int id) {
            return id < Tile.HONOR_START;
        }
    }
}
