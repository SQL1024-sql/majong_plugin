package io.github.sql1024.mahjong.core;

/** 副露 (鳴牌) 的種類。 */
public enum MeldType {
    /** 吃 */
    CHI("吃"),
    /** 碰 */
    PON("碰"),
    /** 暗槓 */
    ANKAN("暗槓"),
    /** 大明槓 */
    MINKAN("明槓"),
    /** 加槓 (小明槓) */
    KAKAN("加槓");

    private final String displayName;

    MeldType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isKan() {
        return this == ANKAN || this == MINKAN || this == KAKAN;
    }

    /** 是否為「暗」的一組 (只有暗槓算暗刻)。 */
    public boolean isConcealed() {
        return this == ANKAN;
    }

    /** 是否會使手牌變成副露 (門前清消失)。暗槓不破壞門前清。 */
    public boolean breaksClosedHand() {
        return this != ANKAN;
    }
}
