package com.majong.riichi.game;

/** Why a hand was thrown out without anybody winning. */
public enum AbortReason {
    NINE_TERMINALS("九種九牌"),
    FOUR_RIICHI("四家立直"),
    FOUR_KANS("四槓散了"),
    FOUR_WINDS("四風連打"),
    THREE_RON("三家和");

    private final String display;

    AbortReason(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
