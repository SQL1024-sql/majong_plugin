package io.github.sql1024.mahjong.core;

/** 役種。 */
public enum Yaku {
    // ---- 1 飜 ----
    RIICHI("立直", "リーチ", 1, 0),
    IPPATSU("一發", "一発", 1, 0),
    MENZEN_TSUMO("門前清自摸和", "門前清自摸和", 1, 0),
    PINFU("平和", "平和", 1, 0),
    TANYAO("斷么九", "断幺九", 1, 1),
    IIPEIKO("一盃口", "一盃口", 1, 0),
    YAKUHAI_HAKU("役牌 白", "白", 1, 1),
    YAKUHAI_HATSU("役牌 發", "發", 1, 1),
    YAKUHAI_CHUN("役牌 中", "中", 1, 1),
    YAKUHAI_ROUND("場風", "場風", 1, 1),
    YAKUHAI_SEAT("自風", "自風", 1, 1),
    HAITEI("海底摸月", "海底摸月", 1, 1),
    HOUTEI("河底撈魚", "河底撈魚", 1, 1),
    RINSHAN("嶺上開花", "嶺上開花", 1, 1),
    CHANKAN("搶槓", "搶槓", 1, 1),

    // ---- 2 飜 ----
    DOUBLE_RIICHI("兩立直", "ダブルリーチ", 2, 0),
    SANSHOKU_DOUJUN("三色同順", "三色同順", 2, 1),
    ITTSU("一氣通貫", "一気通貫", 2, 1),
    CHANTA("混全帶么九", "混全帯幺九", 2, 1),
    CHIITOITSU("七對子", "七対子", 2, 0),
    TOITOI("對對和", "対々和", 2, 2),
    SANANKOU("三暗刻", "三暗刻", 2, 2),
    SANSHOKU_DOUKOU("三色同刻", "三色同刻", 2, 2),
    SANKANTSU("三槓子", "三槓子", 2, 2),
    HONROUTOU("混老頭", "混老頭", 2, 2),
    SHOUSANGEN("小三元", "小三元", 2, 2),

    // ---- 3 飜以上 ----
    HONITSU("混一色", "混一色", 3, 2),
    JUNCHAN("純全帶么九", "純全帯幺九", 3, 2),
    RYANPEIKOU("二盃口", "二盃口", 3, 0),
    CHINITSU("清一色", "清一色", 6, 5),

    // ---- 寶牌 (非役，只計飜) ----
    DORA("寶牌", "ドラ", 0, 0),
    AKA_DORA("赤寶牌", "赤ドラ", 0, 0),
    URA_DORA("裏寶牌", "裏ドラ", 0, 0),

    // ---- 役滿 ----
    KOKUSHI("國士無雙", "国士無双", 1),
    KOKUSHI_13("國士無雙十三面", "国士無双十三面", 2),
    SUUANKOU("四暗刻", "四暗刻", 1),
    SUUANKOU_TANKI("四暗刻單騎", "四暗刻単騎", 2),
    DAISANGEN("大三元", "大三元", 1),
    SHOUSUUSHI("小四喜", "小四喜", 1),
    DAISUUSHI("大四喜", "大四喜", 2),
    TSUUIISOU("字一色", "字一色", 1),
    CHINROUTOU("清老頭", "清老頭", 1),
    RYUUIISOU("綠一色", "緑一色", 1),
    CHUUREN("九蓮寶燈", "九蓮宝燈", 1),
    CHUUREN_9("純正九蓮寶燈", "純正九蓮宝燈", 2),
    SUUKANTSU("四槓子", "四槓子", 1),
    TENHOU("天和", "天和", 1),
    CHIIHOU("地和", "地和", 1);

    private final String chinese;
    private final String japanese;
    private final int closedHan;
    private final int openHan;
    private final int yakumanMultiplier;

    Yaku(String chinese, String japanese, int closedHan, int openHan) {
        this(chinese, japanese, closedHan, openHan, 0);
    }

    Yaku(String chinese, String japanese, int yakumanMultiplier) {
        this(chinese, japanese, 0, 0, yakumanMultiplier);
    }

    Yaku(String chinese, String japanese, int closedHan, int openHan, int yakumanMultiplier) {
        this.chinese = chinese;
        this.japanese = japanese;
        this.closedHan = closedHan;
        this.openHan = openHan;
        this.yakumanMultiplier = yakumanMultiplier;
    }

    public String chinese() {
        return chinese;
    }

    public String japanese() {
        return japanese;
    }

    public boolean isYakuman() {
        return yakumanMultiplier > 0;
    }

    public int yakumanMultiplier() {
        return yakumanMultiplier;
    }

    /** 是否為寶牌 (不是役，不能只靠它和牌)。 */
    public boolean isDora() {
        return this == DORA || this == AKA_DORA || this == URA_DORA;
    }

    /** 副露後是否還成立。 */
    public boolean allowsOpen() {
        return isYakuman() || isDora() || openHan > 0;
    }

    public int han(boolean closed) {
        return closed ? closedHan : openHan;
    }
}
