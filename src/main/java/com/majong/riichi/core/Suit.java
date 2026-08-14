package com.majong.riichi.core;

/** The four tile groups of a japanese mahjong set. */
public enum Suit {
    MAN('m'),
    PIN('p'),
    SOU('s'),
    HONOR('z');

    private final char code;

    Suit(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    public boolean isNumbered() {
        return this != HONOR;
    }

    public static Suit fromCode(char code) {
        for (Suit suit : values()) {
            if (suit.code == code) {
                return suit;
            }
        }
        throw new IllegalArgumentException("unknown suit code: " + code);
    }
}
