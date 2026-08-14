package com.majong.riichi.core;

import java.util.List;

/**
 * Everything outside the tiles themselves that affects what a hand is worth.
 */
public record WinContext(
        Tile winningTile,
        boolean tsumo,
        int seatWind,
        int roundWind,
        boolean riichi,
        boolean doubleRiichi,
        boolean ippatsu,
        boolean rinshan,
        boolean chankan,
        boolean haitei,
        boolean houtei,
        boolean tenhou,
        boolean chiihou,
        boolean openTanyao,
        List<Tile> doraIndicators,
        List<Tile> uraIndicators,
        int honba,
        int riichiSticks) {

    public WinContext {
        doraIndicators = List.copyOf(doraIndicators);
        uraIndicators = List.copyOf(uraIndicators);
    }

    public boolean isDealer() {
        return seatWind == Tiles.EAST;
    }

    public boolean anyRiichi() {
        return riichi || doubleRiichi;
    }

    public static Builder builder(Tile winningTile) {
        return new Builder(winningTile);
    }

    /** Mutable helper so callers only set the flags that apply. */
    public static final class Builder {
        private final Tile winningTile;
        private boolean tsumo;
        private int seatWind = Tiles.EAST;
        private int roundWind = Tiles.EAST;
        private boolean riichi;
        private boolean doubleRiichi;
        private boolean ippatsu;
        private boolean rinshan;
        private boolean chankan;
        private boolean haitei;
        private boolean houtei;
        private boolean tenhou;
        private boolean chiihou;
        private boolean openTanyao = true;
        private List<Tile> doraIndicators = List.of();
        private List<Tile> uraIndicators = List.of();
        private int honba;
        private int riichiSticks;

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

        public Builder riichi(boolean value) {
            this.riichi = value;
            return this;
        }

        public Builder doubleRiichi(boolean value) {
            this.doubleRiichi = value;
            return this;
        }

        public Builder ippatsu(boolean value) {
            this.ippatsu = value;
            return this;
        }

        public Builder rinshan(boolean value) {
            this.rinshan = value;
            return this;
        }

        public Builder chankan(boolean value) {
            this.chankan = value;
            return this;
        }

        public Builder haitei(boolean value) {
            this.haitei = value;
            return this;
        }

        public Builder houtei(boolean value) {
            this.houtei = value;
            return this;
        }

        public Builder tenhou(boolean value) {
            this.tenhou = value;
            return this;
        }

        public Builder chiihou(boolean value) {
            this.chiihou = value;
            return this;
        }

        /** Whether all simples still scores once the hand has been opened. */
        public Builder openTanyao(boolean value) {
            this.openTanyao = value;
            return this;
        }

        public Builder doraIndicators(List<Tile> indicators) {
            this.doraIndicators = indicators;
            return this;
        }

        public Builder uraIndicators(List<Tile> indicators) {
            this.uraIndicators = indicators;
            return this;
        }

        public Builder honba(int value) {
            this.honba = value;
            return this;
        }

        public Builder riichiSticks(int value) {
            this.riichiSticks = value;
            return this;
        }

        public WinContext build() {
            return new WinContext(winningTile, tsumo, seatWind, roundWind, riichi, doubleRiichi,
                    ippatsu, rinshan, chankan, haitei, houtei, tenhou, chiihou, openTanyao,
                    doraIndicators, uraIndicators, honba, riichiSticks);
        }
    }
}
