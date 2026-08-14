package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.List;

/** 役種判定與符數、點數計算。 */
public final class YakuEvaluator {

    private YakuEvaluator() {
    }

    /**
     * 計算一手和牌的最高點數組合。
     *
     * @return 計分結果；若牌型根本不是和牌則回傳 {@code null}
     */
    public static HandScore evaluate(WinContext ctx) {
        List<HandParse> parses = HandParser.parse(ctx.concealedCounts(), ctx.melds());
        if (parses.isEmpty()) {
            return null;
        }
        HandScore best = null;
        for (HandParse parse : parses) {
            for (int winIndex : winGroupCandidates(parse, ctx)) {
                HandScore score = score(parse, winIndex, ctx);
                if (best == null || isBetter(score, best)) {
                    best = score;
                }
            }
        }
        return best;
    }

    /** 這手牌是否有役 (可以和牌)。 */
    public static boolean hasYaku(WinContext ctx) {
        HandScore score = evaluate(ctx);
        return score != null && score.hasYaku();
    }

    private static boolean isBetter(HandScore candidate, HandScore current) {
        if (candidate.hasYaku() != current.hasYaku()) {
            return candidate.hasYaku();
        }
        if (candidate.basePoints() != current.basePoints()) {
            return candidate.basePoints() > current.basePoints();
        }
        if (candidate.yakumanCount() != current.yakumanCount()) {
            return candidate.yakumanCount() > current.yakumanCount();
        }
        if (candidate.han() != current.han()) {
            return candidate.han() > current.han();
        }
        return candidate.fu() > current.fu();
    }

    private static List<Integer> winGroupCandidates(HandParse parse, WinContext ctx) {
        List<Integer> candidates = new ArrayList<>();
        int meldCount = ctx.melds().size();
        List<Group> groups = parse.groups();
        for (int i = 0; i < groups.size(); i++) {
            if (i < meldCount && parse.form() == HandParse.Form.STANDARD) {
                continue;
            }
            if (groups.get(i).contains(ctx.winTile().id())) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(-1);
        }
        return candidates;
    }

    private static HandScore score(HandParse parse, int winIndex, WinContext ctx) {
        boolean closed = ctx.isClosed();
        List<Group> groups = adjustForRon(parse, winIndex, ctx);
        int[] all = Tile.toCounts(ctx.allTiles());
        Rules rules = ctx.rules();

        List<YakuValue> yakuman = findYakuman(parse, groups, winIndex, ctx, closed, all);
        if (!yakuman.isEmpty()) {
            int multiplier = 0;
            for (YakuValue value : yakuman) {
                multiplier += rules.doubleYakuman() ? value.han() : 1;
            }
            return new HandScore(yakuman, 0, 0, multiplier, 8000 * multiplier, limitNameForYakuman(multiplier));
        }

        List<YakuValue> yaku = new ArrayList<>();
        addSituationalYaku(yaku, ctx, closed);
        boolean pinfu = isPinfu(parse, groups, winIndex, ctx, closed);
        if (pinfu) {
            yaku.add(new YakuValue(Yaku.PINFU, 1));
        }
        addHandYaku(yaku, parse, groups, ctx, closed, all);

        int han = 0;
        boolean anyYaku = false;
        for (YakuValue value : yaku) {
            han += value.han();
            anyYaku = true;
        }
        if (!anyYaku) {
            return new HandScore(List.of(), 0, 0, 0, 0, "");
        }

        List<YakuValue> withDora = new ArrayList<>(yaku);
        han += addDora(withDora, ctx, all);

        int fu = computeFu(parse, groups, winIndex, ctx, closed, pinfu);
        return finishScore(withDora, han, fu, rules);
    }

    private static HandScore finishScore(List<YakuValue> yaku, int han, int fu, Rules rules) {
        int basePoints;
        String limitName;
        if (han >= 13 && rules.kazoeYakuman()) {
            basePoints = 8000;
            limitName = "累計役滿";
        } else if (han >= 11) {
            basePoints = 6000;
            limitName = "三倍滿";
        } else if (han >= 8) {
            basePoints = 4000;
            limitName = "倍滿";
        } else if (han >= 6) {
            basePoints = 3000;
            limitName = "跳滿";
        } else if (han == 5) {
            basePoints = 2000;
            limitName = "滿貫";
        } else {
            int raw = fu * (1 << (2 + han));
            if (raw >= 2000 || (rules.kiriageMangan() && raw >= 1920)) {
                basePoints = 2000;
                limitName = "滿貫";
            } else {
                basePoints = raw;
                limitName = "";
            }
        }
        return new HandScore(yaku, han, fu, 0, basePoints, limitName);
    }

    private static String limitNameForYakuman(int multiplier) {
        return multiplier <= 1 ? "役滿" : multiplier + "倍役滿";
    }

    /**
     * 榮和時，和了牌所完成的暗刻要視為明刻。
     */
    private static List<Group> adjustForRon(HandParse parse, int winIndex, WinContext ctx) {
        List<Group> groups = new ArrayList<>(parse.groups());
        if (ctx.tsumo() || winIndex < 0) {
            return groups;
        }
        Group group = groups.get(winIndex);
        if (group.isTriplet() && group.concealed() && !group.kan()) {
            groups.set(winIndex, Group.triplet(group.tileId(), false, false));
        }
        return groups;
    }

    // ------------------------------------------------------------------
    // 役滿
    // ------------------------------------------------------------------

    private static List<YakuValue> findYakuman(HandParse parse, List<Group> groups, int winIndex,
                                               WinContext ctx, boolean closed, int[] all) {
        List<YakuValue> result = new ArrayList<>();

        if (ctx.tenhou()) {
            result.add(new YakuValue(Yaku.TENHOU, 1));
        }
        if (ctx.chiihou()) {
            result.add(new YakuValue(Yaku.CHIIHOU, 1));
        }

        if (parse.form() == HandParse.Form.THIRTEEN_ORPHANS) {
            int[] before = ctx.concealedCounts();
            before[ctx.winTile().id()]--;
            if (HandParser.isThirteenOrphansWait(before)) {
                result.add(new YakuValue(Yaku.KOKUSHI_13, 2));
            } else {
                result.add(new YakuValue(Yaku.KOKUSHI, 1));
            }
            return result;
        }

        boolean allHonors = true;
        boolean allTerminals = true;
        boolean allGreen = true;
        for (int id = 0; id < Tile.KINDS; id++) {
            if (all[id] == 0) {
                continue;
            }
            Tile tile = Tile.of(id);
            allHonors &= tile.isHonor();
            allTerminals &= tile.isTerminal();
            allGreen &= tile.isGreen();
        }
        if (allHonors) {
            result.add(new YakuValue(Yaku.TSUUIISOU, 1));
        }
        if (allTerminals) {
            result.add(new YakuValue(Yaku.CHINROUTOU, 1));
        }
        if (allGreen) {
            result.add(new YakuValue(Yaku.RYUUIISOU, 1));
        }

        if (parse.form() == HandParse.Form.STANDARD) {
            int concealedTriplets = 0;
            int dragonTriplets = 0;
            int windTriplets = 0;
            int kans = 0;
            for (Group group : groups) {
                if (group.isTriplet()) {
                    if (group.concealed()) {
                        concealedTriplets++;
                    }
                    if (group.kan()) {
                        kans++;
                    }
                    Tile tile = Tile.of(group.tileId());
                    if (tile.isDragon()) {
                        dragonTriplets++;
                    }
                    if (tile.isWind()) {
                        windTriplets++;
                    }
                }
            }
            Group pair = parse.pair();
            boolean pairIsWind = pair != null && Tile.of(pair.tileId()).isWind();

            if (concealedTriplets == 4) {
                boolean tanki = winIndex >= 0 && groups.get(winIndex).isPair();
                result.add(tanki ? new YakuValue(Yaku.SUUANKOU_TANKI, 2) : new YakuValue(Yaku.SUUANKOU, 1));
            }
            if (dragonTriplets == 3) {
                result.add(new YakuValue(Yaku.DAISANGEN, 1));
            }
            if (windTriplets == 4) {
                result.add(new YakuValue(Yaku.DAISUUSHI, 2));
            } else if (windTriplets == 3 && pairIsWind) {
                result.add(new YakuValue(Yaku.SHOUSUUSHI, 1));
            }
            if (kans == 4) {
                result.add(new YakuValue(Yaku.SUUKANTSU, 1));
            }
        }

        if (closed) {
            YakuValue chuuren = findChuuren(ctx, all);
            if (chuuren != null) {
                result.add(chuuren);
            }
        }
        return result;
    }

    private static YakuValue findChuuren(WinContext ctx, int[] all) {
        Suit suit = null;
        for (int id = 0; id < Tile.KINDS; id++) {
            if (all[id] == 0) {
                continue;
            }
            Tile tile = Tile.of(id);
            if (tile.isHonor()) {
                return null;
            }
            if (suit == null) {
                suit = tile.suit();
            } else if (suit != tile.suit()) {
                return null;
            }
        }
        if (suit == null) {
            return null;
        }
        int base = Tile.idOf(suit, 1);
        int[] pattern = {3, 1, 1, 1, 1, 1, 1, 1, 3};
        int extras = 0;
        int extraIndex = -1;
        for (int i = 0; i < 9; i++) {
            int diff = all[base + i] - pattern[i];
            if (diff < 0) {
                return null;
            }
            if (diff > 0) {
                extras += diff;
                extraIndex = i;
            }
        }
        if (extras != 1) {
            return null;
        }
        boolean pure = base + extraIndex == ctx.winTile().id();
        return pure ? new YakuValue(Yaku.CHUUREN_9, 2) : new YakuValue(Yaku.CHUUREN, 1);
    }

    // ------------------------------------------------------------------
    // 一般役
    // ------------------------------------------------------------------

    private static void addSituationalYaku(List<YakuValue> yaku, WinContext ctx, boolean closed) {
        if (ctx.doubleRiichi()) {
            yaku.add(new YakuValue(Yaku.DOUBLE_RIICHI, 2));
        } else if (ctx.riichi()) {
            yaku.add(new YakuValue(Yaku.RIICHI, 1));
        }
        if (ctx.ippatsu()) {
            yaku.add(new YakuValue(Yaku.IPPATSU, 1));
        }
        if (ctx.tsumo() && closed) {
            yaku.add(new YakuValue(Yaku.MENZEN_TSUMO, 1));
        }
        if (ctx.haitei()) {
            yaku.add(new YakuValue(Yaku.HAITEI, 1));
        }
        if (ctx.houtei()) {
            yaku.add(new YakuValue(Yaku.HOUTEI, 1));
        }
        if (ctx.rinshan()) {
            yaku.add(new YakuValue(Yaku.RINSHAN, 1));
        }
        if (ctx.chankan()) {
            yaku.add(new YakuValue(Yaku.CHANKAN, 1));
        }
    }

    private static void addHandYaku(List<YakuValue> yaku, HandParse parse, List<Group> groups,
                                    WinContext ctx, boolean closed, int[] all) {
        if (parse.form() == HandParse.Form.SEVEN_PAIRS) {
            yaku.add(new YakuValue(Yaku.CHIITOITSU, 2));
        }

        boolean allSimple = true;
        boolean allTerminalOrHonor = true;
        boolean hasHonor = false;
        boolean hasTerminal = false;
        Suit numberSuit = null;
        boolean multiSuit = false;
        for (int id = 0; id < Tile.KINDS; id++) {
            if (all[id] == 0) {
                continue;
            }
            Tile tile = Tile.of(id);
            allSimple &= tile.isSimple();
            allTerminalOrHonor &= tile.isTerminalOrHonor();
            hasHonor |= tile.isHonor();
            hasTerminal |= tile.isTerminal();
            if (!tile.isHonor()) {
                if (numberSuit == null) {
                    numberSuit = tile.suit();
                } else if (numberSuit != tile.suit()) {
                    multiSuit = true;
                }
            }
        }

        if (allSimple && (closed || ctx.rules().kuitan())) {
            yaku.add(new YakuValue(Yaku.TANYAO, 1));
        }
        if (!multiSuit && numberSuit != null) {
            if (hasHonor) {
                yaku.add(new YakuValue(Yaku.HONITSU, closed ? 3 : 2));
            } else {
                yaku.add(new YakuValue(Yaku.CHINITSU, closed ? 6 : 5));
            }
        }
        if (allTerminalOrHonor && hasHonor && hasTerminal) {
            yaku.add(new YakuValue(Yaku.HONROUTOU, 2));
        }

        if (parse.form() != HandParse.Form.STANDARD) {
            return;
        }

        int sequences = 0;
        int triplets = 0;
        int concealedTriplets = 0;
        int kans = 0;
        int dragonTriplets = 0;
        int[] sequenceStarts = new int[Tile.KINDS];
        int[] tripletTiles = new int[Tile.KINDS];
        boolean everyGroupHasTerminalOrHonor = true;
        boolean everyGroupHasTerminal = true;
        for (Group group : groups) {
            if (group.isSequence()) {
                sequences++;
                sequenceStarts[group.tileId()]++;
            } else if (group.isTriplet()) {
                triplets++;
                tripletTiles[group.tileId()]++;
                if (group.concealed()) {
                    concealedTriplets++;
                }
                if (group.kan()) {
                    kans++;
                }
                if (Tile.of(group.tileId()).isDragon()) {
                    dragonTriplets++;
                }
            }
            everyGroupHasTerminalOrHonor &= group.containsTerminalOrHonor();
            everyGroupHasTerminal &= hasTerminalTile(group);
        }

        for (Group group : groups) {
            if (!group.isTriplet()) {
                continue;
            }
            Tile tile = Tile.of(group.tileId());
            if (tile.id() == Tile.HAKU) {
                yaku.add(new YakuValue(Yaku.YAKUHAI_HAKU, 1));
            } else if (tile.id() == Tile.HATSU) {
                yaku.add(new YakuValue(Yaku.YAKUHAI_HATSU, 1));
            } else if (tile.id() == Tile.CHUN) {
                yaku.add(new YakuValue(Yaku.YAKUHAI_CHUN, 1));
            }
            if (tile.isWind()) {
                Wind wind = Wind.fromTileId(tile.id());
                if (wind == ctx.roundWind()) {
                    yaku.add(new YakuValue(Yaku.YAKUHAI_ROUND, 1));
                }
                if (wind == ctx.seatWind()) {
                    yaku.add(new YakuValue(Yaku.YAKUHAI_SEAT, 1));
                }
            }
        }

        if (closed) {
            int identicalPairs = 0;
            for (int start = 0; start < Tile.KINDS; start++) {
                identicalPairs += sequenceStarts[start] / 2;
            }
            if (identicalPairs >= 2) {
                yaku.add(new YakuValue(Yaku.RYANPEIKOU, 3));
            } else if (identicalPairs == 1) {
                yaku.add(new YakuValue(Yaku.IIPEIKO, 1));
            }
        }

        if (hasSanshokuDoujun(sequenceStarts)) {
            yaku.add(new YakuValue(Yaku.SANSHOKU_DOUJUN, closed ? 2 : 1));
        }
        if (hasIttsu(sequenceStarts)) {
            yaku.add(new YakuValue(Yaku.ITTSU, closed ? 2 : 1));
        }
        if (hasSanshokuDoukou(tripletTiles)) {
            yaku.add(new YakuValue(Yaku.SANSHOKU_DOUKOU, 2));
        }
        if (triplets == 4) {
            yaku.add(new YakuValue(Yaku.TOITOI, 2));
        }
        if (concealedTriplets == 3) {
            yaku.add(new YakuValue(Yaku.SANANKOU, 2));
        }
        if (kans == 3) {
            yaku.add(new YakuValue(Yaku.SANKANTSU, 2));
        }

        Group pair = parse.pair();
        if (dragonTriplets == 2 && pair != null && Tile.of(pair.tileId()).isDragon()) {
            yaku.add(new YakuValue(Yaku.SHOUSANGEN, 2));
        }

        if (sequences > 0 && everyGroupHasTerminalOrHonor && !allTerminalOrHonor) {
            if (everyGroupHasTerminal && !hasHonor) {
                yaku.add(new YakuValue(Yaku.JUNCHAN, closed ? 3 : 2));
            } else if (hasHonor) {
                yaku.add(new YakuValue(Yaku.CHANTA, closed ? 2 : 1));
            }
        }
    }

    private static boolean hasTerminalTile(Group group) {
        if (group.isSequence()) {
            return Tile.of(group.tileId()).isTerminal() || Tile.of(group.tileId() + 2).isTerminal();
        }
        return Tile.of(group.tileId()).isTerminal();
    }

    private static boolean hasSanshokuDoujun(int[] sequenceStarts) {
        for (int number = 1; number <= 7; number++) {
            if (sequenceStarts[Tile.MAN_START + number - 1] > 0
                    && sequenceStarts[Tile.PIN_START + number - 1] > 0
                    && sequenceStarts[Tile.SOU_START + number - 1] > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSanshokuDoukou(int[] tripletTiles) {
        for (int number = 1; number <= 9; number++) {
            if (tripletTiles[Tile.MAN_START + number - 1] > 0
                    && tripletTiles[Tile.PIN_START + number - 1] > 0
                    && tripletTiles[Tile.SOU_START + number - 1] > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIttsu(int[] sequenceStarts) {
        int[] suitStarts = {Tile.MAN_START, Tile.PIN_START, Tile.SOU_START};
        for (int start : suitStarts) {
            if (sequenceStarts[start] > 0 && sequenceStarts[start + 3] > 0 && sequenceStarts[start + 6] > 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 平和 / 符
    // ------------------------------------------------------------------

    private static boolean isPinfu(HandParse parse, List<Group> groups, int winIndex,
                                   WinContext ctx, boolean closed) {
        if (!closed || parse.form() != HandParse.Form.STANDARD || winIndex < 0) {
            return false;
        }
        for (Group group : groups) {
            if (group.isTriplet()) {
                return false;
            }
        }
        Group pair = parse.pair();
        if (pair == null || isYakuhai(pair.tileId(), ctx)) {
            return false;
        }
        Group winGroup = groups.get(winIndex);
        return winGroup.isSequence() && waitFu(winGroup, ctx.winTile().id()) == 0;
    }

    private static boolean isYakuhai(int tileId, WinContext ctx) {
        Tile tile = Tile.of(tileId);
        if (tile.isDragon()) {
            return true;
        }
        if (!tile.isWind()) {
            return false;
        }
        Wind wind = Wind.fromTileId(tileId);
        return wind == ctx.roundWind() || wind == ctx.seatWind();
    }

    /** 待ち的符數：兩面 / 雙碰 0 符，坎張 / 邊張 / 單騎 2 符。 */
    private static int waitFu(Group group, int winTileId) {
        if (group.isPair()) {
            return 2;
        }
        if (!group.isSequence()) {
            return 0;
        }
        int start = group.tileId();
        if (winTileId == start + 1) {
            return 2;
        }
        if (winTileId == start && Tile.of(start + 2).number() == 9) {
            return 2;
        }
        if (winTileId == start + 2 && Tile.of(start).number() == 1) {
            return 2;
        }
        return 0;
    }

    private static int computeFu(HandParse parse, List<Group> groups, int winIndex,
                                 WinContext ctx, boolean closed, boolean pinfu) {
        if (parse.form() == HandParse.Form.SEVEN_PAIRS) {
            return 25;
        }
        if (pinfu) {
            return ctx.tsumo() ? 20 : 30;
        }

        int fu = 20;
        if (closed && !ctx.tsumo()) {
            fu += 10;
        }
        if (ctx.tsumo()) {
            fu += 2;
        }

        for (Group group : groups) {
            if (group.isPair()) {
                if (isYakuhai(group.tileId(), ctx)) {
                    fu += 2;
                }
            } else if (group.isTriplet()) {
                int value = Tile.of(group.tileId()).isTerminalOrHonor() ? 4 : 2;
                if (group.kan()) {
                    value *= 4;
                }
                if (group.concealed()) {
                    value *= 2;
                }
                fu += value;
            }
        }

        if (winIndex >= 0) {
            fu += waitFu(groups.get(winIndex), ctx.winTile().id());
        }

        fu = ((fu + 9) / 10) * 10;
        if (!closed && fu == 20) {
            fu = 30;
        }
        return fu;
    }

    // ------------------------------------------------------------------
    // 寶牌
    // ------------------------------------------------------------------

    private static int addDora(List<YakuValue> yaku, WinContext ctx, int[] all) {
        int total = 0;
        int dora = countDora(ctx.doraIndicators(), all);
        if (dora > 0) {
            yaku.add(new YakuValue(Yaku.DORA, dora));
            total += dora;
        }
        int aka = 0;
        for (Tile tile : ctx.allTiles()) {
            if (tile.isRed()) {
                aka++;
            }
        }
        if (aka > 0) {
            yaku.add(new YakuValue(Yaku.AKA_DORA, aka));
            total += aka;
        }
        if (ctx.riichi()) {
            int ura = countDora(ctx.uraIndicators(), all);
            if (ura > 0) {
                yaku.add(new YakuValue(Yaku.URA_DORA, ura));
                total += ura;
            }
        }
        return total;
    }

    private static int countDora(List<Tile> indicators, int[] all) {
        int count = 0;
        for (Tile indicator : indicators) {
            count += all[indicator.doraFromIndicator().id()];
        }
        return count;
    }
}
