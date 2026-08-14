package io.github.sql1024.mahjong.core;

/** 牌的花色 (suit)。 */
public enum Suit {
    /** 萬子 */
    MAN('m', "萬子"),
    /** 筒子 */
    PIN('p', "筒子"),
    /** 索子 */
    SOU('s', "索子"),
    /** 字牌 */
    HONOR('z', "字牌");

    private final char code;
    private final String displayName;

    Suit(char code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public char code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isNumbered() {
        return this != HONOR;
    }

    public static Suit fromCode(char c) {
        for (Suit s : values()) {
            if (s.code == c) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown suit code: " + c);
    }
}
