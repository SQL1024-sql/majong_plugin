package com.majong.riichi.core;

/** The ways a group of tiles can be melded. */
public enum MeldType {
    /** Sequence claimed from the player to the left. */
    CHI(3, true),
    /** Triplet claimed from any opponent. */
    PON(3, true),
    /** Closed kan, declared from four tiles in hand. */
    ANKAN(4, false),
    /** Open kan claimed from an opponent's discard. */
    DAIMINKAN(4, true),
    /** Open kan upgraded from an existing pon. */
    SHOUMINKAN(4, true);

    private final int size;
    private final boolean open;

    MeldType(int size, boolean open) {
        this.size = size;
        this.open = open;
    }

    public int size() {
        return size;
    }

    /** Whether having this meld breaks a closed hand. */
    public boolean isOpen() {
        return open;
    }

    public boolean isKan() {
        return size == 4;
    }
}
