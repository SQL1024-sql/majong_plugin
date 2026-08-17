package com.majong.riichi.game;

/**
 * The table settings a hand is played under.
 *
 * @param variant         which game is being dealt
 * @param startingPoints  points every player starts with
 * @param finalRoundWind  last wind played, east for a short game and south for a half game
 * @param redFives        whether one five of each suit is a red dora
 * @param openTanyao      whether all simples scores in an open hand
 * @param headBump        whether only the closest player to the discarder may ron
 * @param endOnBankruptcy whether the game stops as soon as somebody goes below zero
 */
public record GameRules(Variant variant, int startingPoints, int finalRoundWind,
                        boolean redFives, boolean openTanyao, boolean headBump,
                        boolean endOnBankruptcy) {

    /** A half game to south four, the usual japanese ruleset. */
    public static GameRules hanchan() {
        return new GameRules(Variant.JAPANESE, 25000, com.majong.riichi.core.Tiles.SOUTH,
                true, true, true, true);
    }

    /** A short japanese game that ends after the east round. */
    public static GameRules tonpuusen() {
        return new GameRules(Variant.JAPANESE, 25000, com.majong.riichi.core.Tiles.EAST,
                true, true, true, true);
    }

    /** A taiwanese sixteen tile game, played to the end of the south round. */
    public static GameRules taiwanese() {
        return new GameRules(Variant.TAIWANESE, 0, com.majong.riichi.core.Tiles.SOUTH,
                false, true, true, false);
    }
}
