package com.majong.riichi.game;

import com.majong.riichi.core.Tile;

/** Something a player can do when the table is waiting on them. */
public sealed interface Action {

    /** Put a tile down, optionally declaring riichi in the same move. */
    record Discard(Tile tile, boolean riichi) implements Action {
    }

    /** Win on the tile just drawn. */
    record Tsumo() implements Action {
    }

    /** Win on the tile just discarded, or on a tile added to an open triplet. */
    record Ron() implements Action {
    }

    /** Claim a discard to make a run, using the two given tiles from hand. */
    record Chi(Tile first, Tile second) implements Action {
    }

    /** Claim a discard to make a triplet. */
    record Pon() implements Action {
    }

    /**
     * Declare a kan of the given tile kind: closed from four in hand, added to
     * an existing triplet, or claimed from a discard.
     */
    record Kan(int kind) implements Action {
    }

    /** Decline whatever was on offer. */
    record Pass() implements Action {
    }

    /** Abort the hand on the first turn holding nine or more terminals and honours. */
    record NineTerminals() implements Action {
    }
}
