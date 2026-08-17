package com.majong.riichi.taiwan;

/**
 * The scoring patterns of taiwanese sixteen tile mahjong, with the tai each is
 * worth by default.
 *
 * <p>House rules vary a great deal, which is why every value here is only a
 * starting point; the table is overridable from configuration.
 */
public enum Tai {
    MENQING("門清", 1),
    ZIMO("自摸", 1),
    MENQING_ZIMO("門清自摸", 1),
    PINGHU("平胡", 2),
    DUANYAOJIU("斷么九", 1),
    QUANQIUREN("全求人", 2),
    DANDIAO("獨聽", 1),

    YAKUHAI_ZHONG("中", 1),
    YAKUHAI_FA("發", 1),
    YAKUHAI_BAI("白", 1),
    SEAT_WIND("門風", 1),
    ROUND_WIND("圈風", 1),

    SANANKE("三暗刻", 2),
    SIANKE("四暗刻", 5),
    WUANKE("五暗刻", 8),
    PENGPENGHU("碰碰胡", 4),
    HUNYISE("混一色", 4),
    QINGYISE("清一色", 8),
    HUNLAOTOU("混老頭", 4),
    QINGLAOTOU("清老頭", 8),
    XIAOSANYUAN("小三元", 4),
    DASANYUAN("大三元", 8),
    XIAOSIXI("小四喜", 8),
    DASIXI("大四喜", 16),
    ZIYISE("字一色", 16),

    GANGSHANG("槓上開花", 1),
    QIANGGANG("搶槓", 1),
    HAIDI("海底撈月", 1),
    HEDI("河底撈魚", 1),

    TIANHU("天胡", 16),
    DIHU("地胡", 16),
    RENHU("人胡", 8),

    FLOWER("花牌", 1),
    FLOWER_GANG("花槓", 2),

    DEALER("莊家", 1),
    DEALER_STREAK("連莊拉莊", 2);

    private final String display;
    private final int defaultTai;

    Tai(String display, int defaultTai) {
        this.display = display;
        this.defaultTai = defaultTai;
    }

    public String display() {
        return display;
    }

    public int defaultTai() {
        return defaultTai;
    }

    /** The key this pattern is read under in config.yml. */
    public String configKey() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
