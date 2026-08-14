package io.github.sql1024.mahjong.game;

/** 玩家可以宣告的動作。 */
public enum CallType {
    CHI("吃"),
    PON("碰"),
    KAN("槓"),
    RON("榮"),
    TSUMO("自摸"),
    RIICHI("立直"),
    KYUUSHU("九種九牌"),
    PASS("略過");

    private final String displayName;

    CallType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 鳴牌的優先順序，數字越大越優先。 */
    public int priority() {
        return switch (this) {
            case RON -> 3;
            case KAN, PON -> 2;
            case CHI -> 1;
            default -> 0;
        };
    }
}
