package com.majong.riichi.game;

import com.majong.riichi.core.WinChecker;

/** The two games this plugin knows how to deal. */
public enum Variant {

    /** Japanese riichi: thirteen tiles, four sets and a pair, dora and riichi. */
    JAPANESE("日本立直麻雀", 13, WinChecker.JAPANESE_SETS, false, true, true, true),

    /** Taiwanese sixteen tile: five sets and a pair, flowers, scored in tai. */
    TAIWANESE("台灣十六張", 16, WinChecker.TAIWANESE_SETS, true, false, false, false);

    private final String display;
    private final int handSize;
    private final int totalSets;
    private final boolean flowers;
    private final boolean riichi;
    private final boolean dora;
    private final boolean tenpaiPayments;

    Variant(String display, int handSize, int totalSets, boolean flowers, boolean riichi,
            boolean dora, boolean tenpaiPayments) {
        this.display = display;
        this.handSize = handSize;
        this.totalSets = totalSets;
        this.flowers = flowers;
        this.riichi = riichi;
        this.dora = dora;
        this.tenpaiPayments = tenpaiPayments;
    }

    public String display() {
        return display;
    }

    /** Tiles dealt to each player before the first draw. */
    public int handSize() {
        return handSize;
    }

    /** Sets a finished hand is made of, not counting the pair. */
    public int totalSets() {
        return totalSets;
    }

    public boolean hasFlowers() {
        return flowers;
    }

    public boolean allowsRiichi() {
        return riichi;
    }

    public boolean hasDora() {
        return dora;
    }

    /** Whether ready hands collect from the rest when the wall runs out. */
    public boolean hasTenpaiPayments() {
        return tenpaiPayments;
    }

    /** Reads the value of the {@code variant} setting, defaulting to japanese. */
    public static Variant parse(String name) {
        if (name == null) {
            return JAPANESE;
        }
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "taiwan", "taiwanese", "tw", "台灣", "台湾", "16" -> TAIWANESE;
            default -> JAPANESE;
        };
    }
}
