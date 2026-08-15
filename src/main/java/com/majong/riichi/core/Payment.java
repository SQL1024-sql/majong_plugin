package com.majong.riichi.core;

/**
 * Who pays what for a won hand, honba and riichi sticks included.
 *
 * @param total          everything the winner collects
 * @param fromDiscarder  paid by the player who dealt into the hand, {@code 0} on a self draw
 * @param fromDealer     paid by the dealer on a non dealer self draw
 * @param fromNonDealer  paid by each of the other players on a self draw
 * @param honbaBonus     part of the total that comes from repeat counters
 * @param riichiSticks   points collected from riichi sticks on the table
 */
public record Payment(int total, int fromDiscarder, int fromDealer, int fromNonDealer,
                      int honbaBonus, int riichiSticks) {

    public boolean isTsumo() {
        return fromDiscarder == 0;
    }
}
