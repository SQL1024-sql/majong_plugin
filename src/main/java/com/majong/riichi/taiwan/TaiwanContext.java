package com.majong.riichi.taiwan;

import com.majong.riichi.core.Flower;
import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import java.util.List;

/** Everything outside the tiles that decides what a taiwanese hand is worth. */
public record TaiwanContext(
        Tile winningTile,
        boolean tsumo,
        int seatWind,
        int roundWind,
        int dealerStreak,
        List<Flower> flowers,
        boolean afterKan,
        boolean robbingKan,
        boolean lastDraw,
        boolean lastDiscard,
        boolean firstTurn) {

    public TaiwanContext {
        flowers = List.copyOf(flowers);
    }

    public boolean isDealer() {
        return seatWind == Tiles.EAST;
    }

    /** The seat's index counted from the dealer, which is what flowers match. */
    public int seatIndex() {
        return seatWind - Tiles.EAST;
    }

    public static Builder builder(Tile winningTile) {
        return new Builder(winningTile);
    }

    /** Mutable helper so callers only set what applies. */
    public static final class Builder {
        private final Tile winningTile;
        private boolean tsumo;
        private int seatWind = Tiles.EAST;
        private int roundWind = Tiles.EAST;
        private int dealerStreak;
        private List<Flower> flowers = List.of();
        private boolean afterKan;
        private boolean robbingKan;
        private boolean lastDraw;
        private boolean lastDiscard;
        private boolean firstTurn;

        private Builder(Tile winningTile) {
            this.winningTile = winningTile;
        }

        public Builder tsumo(boolean value) {
            this.tsumo = value;
            return this;
        }

        public Builder seatWind(int kind) {
            this.seatWind = kind;
            return this;
        }

        public Builder roundWind(int kind) {
            this.roundWind = kind;
            return this;
        }

        /** How many hands the dealer has kept their seat for. */
        public Builder dealerStreak(int value) {
            this.dealerStreak = value;
            return this;
        }

        public Builder flowers(List<Flower> value) {
            this.flowers = value;
            return this;
        }

        public Builder afterKan(boolean value) {
            this.afterKan = value;
            return this;
        }

        public Builder robbingKan(boolean value) {
            this.robbingKan = value;
            return this;
        }

        public Builder lastDraw(boolean value) {
            this.lastDraw = value;
            return this;
        }

        public Builder lastDiscard(boolean value) {
            this.lastDiscard = value;
            return this;
        }

        /** Set when the hand was complete before anybody discarded. */
        public Builder firstTurn(boolean value) {
            this.firstTurn = value;
            return this;
        }

        public TaiwanContext build() {
            return new TaiwanContext(winningTile, tsumo, seatWind, roundWind, dealerStreak,
                    flowers, afterKan, robbingKan, lastDraw, lastDiscard, firstTurn);
        }
    }
}
