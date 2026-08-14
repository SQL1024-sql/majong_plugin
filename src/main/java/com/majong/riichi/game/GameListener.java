package com.majong.riichi.game;

import com.majong.riichi.core.Meld;
import com.majong.riichi.core.Tile;
import java.util.List;

/** Notified as a hand plays out so a front end can show what happened. */
public interface GameListener {

    default void onHandStarted(RiichiGame game) {
    }

    /** It is this seat's turn to act on the tile they just drew. */
    default void onTurn(RiichiGame game, int seat, Tile drawn) {
    }

    default void onDiscard(RiichiGame game, int seat, Tile tile, boolean riichi) {
    }

    /** These seats may claim the tile that was just discarded. */
    default void onCallWindow(RiichiGame game, List<Integer> seats) {
    }

    default void onCall(RiichiGame game, int seat, Meld meld) {
    }

    default void onDoraRevealed(RiichiGame game, Tile indicator) {
    }

    default void onHandFinished(RiichiGame game, HandResult result) {
    }

    default void onGameFinished(RiichiGame game) {
    }
}
