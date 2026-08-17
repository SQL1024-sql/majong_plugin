package com.majong.riichi.core;

/**
 * Every scoring pattern the plugin recognises, with its value closed and open.
 * An open value of {@code 0} marks a yaku that only counts in a closed hand.
 */
public enum Yaku {
    RIICHI("立直", 1, 0),
    IPPATSU("一發", 1, 0),
    MENZEN_TSUMO("門前清自摸", 1, 0),
    PINFU("平胡", 1, 0),
    TANYAO("斷么九", 1, 1),
    IIPEIKO("一盃口", 1, 0),
    YAKUHAI_HAKU("三元牌 白板", 1, 1),
    YAKUHAI_HATSU("三元牌 發財", 1, 1),
    YAKUHAI_CHUN("三元牌 紅中", 1, 1),
    YAKUHAI_SEAT("門風牌", 1, 1),
    YAKUHAI_ROUND("圈風牌", 1, 1),
    RINSHAN("槓上開花", 1, 1),
    CHANKAN("搶槓", 1, 1),
    HAITEI("海底撈月", 1, 1),
    HOUTEI("河底撈魚", 1, 1),
    DOUBLE_RIICHI("雙立直", 2, 0),
    CHIITOITSU("七對子", 2, 0),
    SANSHOKU_DOUJUN("三色同順", 2, 1),
    ITTSU("一氣通貫", 2, 1),
    CHANTA("混全帶么九", 2, 1),
    HONROUTOU("混老頭", 2, 2),
    TOITOI("對對胡", 2, 2),
    SANANKOU("三暗刻", 2, 2),
    SANSHOKU_DOUKOU("三色同刻", 2, 2),
    SANKANTSU("三槓子", 2, 2),
    SHOUSANGEN("小三元", 2, 2),
    HONITSU("混一色", 3, 2),
    JUNCHAN("純全帶么九", 3, 2),
    RYANPEIKOU("二盃口", 3, 0),
    CHINITSU("清一色", 6, 5),

    KOKUSHI("國士無雙", 1),
    KOKUSHI_JUUSANMEN("國士無雙十三面", 2),
    SUUANKOU("四暗刻", 1),
    SUUANKOU_TANKI("四暗刻單騎", 2),
    DAISANGEN("大三元", 1),
    SHOUSUUSHII("小四喜", 1),
    DAISUUSHII("大四喜", 2),
    TSUUIISOU("字一色", 1),
    CHINROUTOU("清老頭", 1),
    RYUUIISOU("綠一色", 1),
    CHUUREN("九蓮寶燈", 1),
    CHUUREN_JUNSEI("純正九蓮寶燈", 2),
    SUUKANTSU("四槓子", 1),
    TENHOU("天胡", 1),
    CHIIHOU("地胡", 1);

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
