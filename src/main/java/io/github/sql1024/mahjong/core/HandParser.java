package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.List;

/** 把手牌拆解成面子與雀頭。 */
public final class HandParser {

    /** 國士無雙需要的 13 種么九牌。 */
    public static final int[] ORPHANS = {
            0, 8, 9, 17, 18, 26,
            Tile.EAST, Tile.SOUTH, Tile.WEST, Tile.NORTH, Tile.HAKU, Tile.HATSU, Tile.CHUN
    };

    private HandParser() {
    }

    /**
     * 列出所有可能的和牌拆解方式；若不是和牌則回傳空清單。
     *
     * @param concealed 門前手牌 (含和了牌) 的 34 種計數
     * @param melds     已副露的組合
     */
    public static List<HandParse> parse(int[] concealed, List<Meld> melds) {
        List<HandParse> results = new ArrayList<>();
        int concealedTiles = 0;
        for (int count : concealed) {
            concealedTiles += count;
        }
        if (concealedTiles != 14 - 3 * melds.size()) {
            return results;
        }

        List<Group> meldGroups = new ArrayList<>(melds.size());
        for (Meld meld : melds) {
            meldGroups.add(meld.toGroup());
        }

        int[] counts = concealed.clone();
        for (int pair = 0; pair < Tile.KINDS; pair++) {
            if (counts[pair] < 2) {
                continue;
            }
            counts[pair] -= 2;
            List<List<Group>> decompositions = new ArrayList<>();
            collectMelds(counts, 0, true, new ArrayList<>(), decompositions);
            counts[pair] += 2;
            for (List<Group> decomposition : decompositions) {
                List<Group> groups = new ArrayList<>(meldGroups);
                groups.addAll(decomposition);
                groups.add(Group.pair(pair));
                results.add(new HandParse(HandParse.Form.STANDARD, groups));
            }
        }

        if (melds.isEmpty()) {
            if (isSevenPairs(concealed)) {
                List<Group> groups = new ArrayList<>();
                for (int id = 0; id < Tile.KINDS; id++) {
                    if (concealed[id] == 2) {
                        groups.add(Group.pair(id));
                    }
                }
                results.add(new HandParse(HandParse.Form.SEVEN_PAIRS, groups));
            }
            if (isThirteenOrphans(concealed)) {
                List<Group> groups = new ArrayList<>();
                for (int orphan : ORPHANS) {
                    if (concealed[orphan] == 2) {
                        groups.add(Group.pair(orphan));
                    }
                }
                results.add(new HandParse(HandParse.Form.THIRTEEN_ORPHANS, groups));
            }
        }
        return results;
    }

    /** 這副牌是否已經和牌。 */
    public static boolean isWinningHand(int[] concealed, List<Meld> melds) {
        return !parse(concealed, melds).isEmpty();
    }

    public static boolean isSevenPairs(int[] counts) {
        int pairs = 0;
        for (int count : counts) {
            if (count == 2) {
                pairs++;
            } else if (count != 0) {
                return false;
            }
        }
        return pairs == 7;
    }

    public static boolean isThirteenOrphans(int[] counts) {
        int total = 0;
        int pairs = 0;
        for (int orphan : ORPHANS) {
            int count = counts[orphan];
            if (count == 0) {
                return false;
            }
            if (count == 2) {
                pairs++;
            } else if (count != 1) {
                return false;
            }
            total += count;
        }
        int all = 0;
        for (int count : counts) {
            all += count;
        }
        return pairs == 1 && total == 14 && all == 14;
    }

    /** 國士無雙十三面 (聽牌時 13 張互不相同)。 */
    public static boolean isThirteenOrphansWait(int[] concealedWithoutWinTile) {
        for (int orphan : ORPHANS) {
            if (concealedWithoutWinTile[orphan] != 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param allowTriplet 目前這個位置是否還能組刻子；同一張牌先取順子後就不再回頭取刻子，
     *                     以免同一種拆解被列舉兩次。
     */
    private static void collectMelds(int[] counts, int start, boolean allowTriplet,
                                     List<Group> acc, List<List<Group>> out) {
        int index = start;
        boolean triplet = allowTriplet;
        while (index < Tile.KINDS && counts[index] == 0) {
            index++;
            triplet = true;
        }
        if (index == Tile.KINDS) {
            out.add(new ArrayList<>(acc));
            return;
        }

        if (triplet && counts[index] >= 3) {
            counts[index] -= 3;
            acc.add(Group.triplet(index, true, false));
            collectMelds(counts, index, true, acc, out);
            acc.removeLast();
            counts[index] += 3;
        }

        if (canStartSequence(index) && counts[index + 1] > 0 && counts[index + 2] > 0) {
            counts[index]--;
            counts[index + 1]--;
            counts[index + 2]--;
            acc.add(Group.sequence(index, true));
            collectMelds(counts, index, false, acc, out);
            acc.removeLast();
            counts[index]++;
            counts[index + 1]++;
            counts[index + 2]++;
        }
    }

    static boolean canStartSequence(int id) {
        if (id >= Tile.HONOR_START) {
            return false;
        }
        return Tile.of(id).number() <= 7;
    }
}
