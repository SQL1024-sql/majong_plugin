package com.majong.riichi.core;

/**
 * Every scoring pattern the plugin recognises, with its value closed and open.
 * An open value of {@code 0} marks a yaku that only counts in a closed hand.
 */
public enum Yaku {
    RIICHI("立直", 1, 0),
    IPPATSU("一発", 1, 0),
    MENZEN_TSUMO("門前清自摸和", 1, 0),
    PINFU("平和", 1, 0),
    TANYAO("断幺九", 1, 1),
    IIPEIKO("一盃口", 1, 0),
    YAKUHAI_HAKU("役牌 白", 1, 1),
    YAKUHAI_HATSU("役牌 發", 1, 1),
    YAKUHAI_CHUN("役牌 中", 1, 1),
    YAKUHAI_SEAT("自風", 1, 1),
    YAKUHAI_ROUND("場風", 1, 1),
    RINSHAN("嶺上開花", 1, 1),
    CHANKAN("搶槓", 1, 1),
    HAITEI("海底摸月", 1, 1),
    HOUTEI("河底撈魚", 1, 1),
    DOUBLE_RIICHI("ダブル立直", 2, 0),
    CHIITOITSU("七対子", 2, 0),
    SANSHOKU_DOUJUN("三色同順", 2, 1),
    ITTSU("一気通貫", 2, 1),
    CHANTA("混全帯幺九", 2, 1),
    HONROUTOU("混老頭", 2, 2),
    TOITOI("対々和", 2, 2),
    SANANKOU("三暗刻", 2, 2),
    SANSHOKU_DOUKOU("三色同刻", 2, 2),
    SANKANTSU("三槓子", 2, 2),
    SHOUSANGEN("小三元", 2, 2),
    HONITSU("混一色", 3, 2),
    JUNCHAN("純全帯幺九", 3, 2),
    RYANPEIKOU("二盃口", 3, 0),
    CHINITSU("清一色", 6, 5),

    KOKUSHI("国士無双", 1),
    KOKUSHI_JUUSANMEN("国士無双十三面", 2),
    SUUANKOU("四暗刻", 1),
    SUUANKOU_TANKI("四暗刻単騎", 2),
    DAISANGEN("大三元", 1),
    SHOUSUUSHII("小四喜", 1),
    DAISUUSHII("大四喜", 2),
    TSUUIISOU("字一色", 1),
    CHINROUTOU("清老頭", 1),
    RYUUIISOU("緑一色", 1),
    CHUUREN("九蓮宝燈", 1),
    CHUUREN_JUNSEI("純正九蓮宝燈", 2),
    SUUKANTSU("四槓子", 1),
    TENHOU("天和", 1),
    CHIIHOU("地和", 1);

    private final String display;
    private final int closedHan;
    private final int openHan;
    private final int yakumanMultiplier;

    Yaku(String display, int closedHan, int openHan) {
        this.display = display;
        this.closedHan = closedHan;
        this.openHan = openHan;
        this.yakumanMultiplier = 0;
    }

    Yaku(String display, int yakumanMultiplier) {
        this.display = display;
        this.closedHan = 0;
        this.openHan = 0;
        this.yakumanMultiplier = yakumanMultiplier;
    }

    public String display() {
        return display;
    }

    public boolean isYakuman() {
        return yakumanMultiplier > 0;
    }

    public int yakumanMultiplier() {
        return yakumanMultiplier;
    }

    public boolean isClosedOnly() {
        return !isYakuman() && openHan == 0;
    }

    /** Value in han for a hand that is (or is not) closed; {@code 0} if it does not apply. */
    public int han(boolean closed) {
        if (isYakuman()) {
            return 0;
        }
        return closed ? closedHan : openHan;
    }
}
