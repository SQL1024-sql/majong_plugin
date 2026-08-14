package com.majong.riichi.core;

/** The shape the winning tile completed. */
public enum WaitType {
    /** Open ended run, e.g. 34 waiting on 2 or 5. */
    RYANMEN(0),
    /** Closed run, e.g. 35 waiting on 4. */
    KANCHAN(2),
    /** Edge run, e.g. 12 waiting on 3. */
    PENCHAN(2),
    /** Two pairs waiting to become a triplet. */
    SHANPON(0),
    /** A lone tile waiting to become the pair. */
    TANKI(2);

    private final int fu;

    WaitType(int fu) {
        this.fu = fu;
    }

    public int fu() {
        return fu;
    }
}
