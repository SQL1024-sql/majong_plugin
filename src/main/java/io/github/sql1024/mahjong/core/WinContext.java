package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.List;

/** 和牌時的所有情報，計算役種與符數用。 */
public final class WinContext {

    private final List<Tile> concealed;
    private final List<Meld> melds;
    private final Tile winTile;
    private final boolean tsumo;
    private final Wind seatWind;
    private final Wind roundWind;
    private final boolean riichi;
    private final boolean doubleRiichi;
    private final boolean ippatsu;
    private final boolean haitei;
    private final boolean houtei;
    private final boolean rinshan;
    private final boolean chankan;
    private final boolean tenhou;
    private final boolean chiihou;
    private final List<Tile> doraIndicators;
    private final List<Tile> uraIndicators;
    private final Rules rules;

    private WinContext(Builder builder) {
        this.concealed = List.copyOf(builder.concealed);
        this.melds = List.copyOf(builder.melds);
        this.winTile = builder.winTile;
        this.tsumo = builder.tsumo;
        this.seatWind = builder.seatWind;
        this.roundWind = builder.roundWind;
        this.riichi = builder.riichi;
        this.doubleRiichi = builder.doubleRiichi;
        this.ippatsu = builder.ippatsu;
        this.haitei = builder.haitei;
        this.houtei = builder.houtei;
        this.rinshan = builder.rinshan;
        this.chankan = builder.chankan;
        this.tenhou = builder.tenhou;
        this.chiihou = builder.chiihou;
        this.doraIndicators = List.copyOf(builder.doraIndicators);
        this.uraIndicators = List.copyOf(builder.uraIndicators);
        this.rules = builder.rules;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 門前手牌，含和了牌。 */
    public List<Tile> concealed() {
        return concealed;
    }

    public List<Meld> melds() {
        return melds;
    }

    public Tile winTile() {
        return winTile;
    }

    public boolean tsumo() {
        return tsumo;
    }

    public Wind seatWind() {
        return seatWind;
    }

    public Wind roundWind() {
        return roundWind;
    }

    public boolean riichi() {
        return riichi || doubleRiichi;
    }

    public boolean doubleRiichi() {
        return doubleRiichi;
    }

    public boolean ippatsu() {
        return ippatsu;
    }

    public boolean haitei() {
        return haitei;
    }

    public boolean houtei() {
        return houtei;
    }

    public boolean rinshan() {
        return rinshan;
    }

    public boolean chankan() {
        return chankan;
    }

    public boolean tenhou() {
        return tenhou;
    }

    public boolean chiihou() {
        return chiihou;
    }

    public List<Tile> doraIndicators() {
        return doraIndicators;
    }

    public List<Tile> uraIndicators() {
        return uraIndicators;
    }

    public Rules rules() {
        return rules;
    }

    /** 是否門前清 (暗槓不算破壞門前)。 */
    public boolean isClosed() {
        for (Meld meld : melds) {
            if (meld.type().breaksClosedHand()) {
                return false;
            }
        }
        return true;
    }

    /** 手牌全部的牌 (門前 + 副露)。 */
    public List<Tile> allTiles() {
        List<Tile> all = new ArrayList<>(concealed);
        for (Meld meld : melds) {
            all.addAll(meld.tiles());
        }
        return all;
    }

    public int[] concealedCounts() {
        return Tile.toCounts(concealed);
    }

    public static final class Builder {
        private List<Tile> concealed = List.of();
        private List<Meld> melds = List.of();
        private Tile winTile;
        private boolean tsumo;
        private Wind seatWind = Wind.EAST;
        private Wind roundWind = Wind.EAST;
        private boolean riichi;
        private boolean doubleRiichi;
        private boolean ippatsu;
        private boolean haitei;
        private boolean houtei;
        private boolean rinshan;
        private boolean chankan;
        private boolean tenhou;
        private boolean chiihou;
        private List<Tile> doraIndicators = List.of();
        private List<Tile> uraIndicators = List.of();
        private Rules rules = Rules.defaults();

        public Builder concealed(List<Tile> tiles) {
            this.concealed = tiles;
            return this;
        }

        public Builder concealed(String notation) {
            this.concealed = Tile.parseHand(notation);
            return this;
        }

        public Builder melds(List<Meld> melds) {
            this.melds = melds;
            return this;
        }

        public Builder winTile(Tile tile) {
            this.winTile = tile;
            return this;
        }

        public Builder winTile(String notation) {
            this.winTile = Tile.parse(notation);
            return this;
        }

        public Builder tsumo(boolean tsumo) {
            this.tsumo = tsumo;
            return this;
        }

        public Builder seatWind(Wind wind) {
            this.seatWind = wind;
            return this;
        }

        public Builder roundWind(Wind wind) {
            this.roundWind = wind;
            return this;
        }

        public Builder riichi(boolean riichi) {
            this.riichi = riichi;
            return this;
        }

        public Builder doubleRiichi(boolean doubleRiichi) {
            this.doubleRiichi = doubleRiichi;
            return this;
        }

        public Builder ippatsu(boolean ippatsu) {
            this.ippatsu = ippatsu;
            return this;
        }

        public Builder haitei(boolean haitei) {
            this.haitei = haitei;
            return this;
        }

        public Builder houtei(boolean houtei) {
            this.houtei = houtei;
            return this;
        }

        public Builder rinshan(boolean rinshan) {
            this.rinshan = rinshan;
            return this;
        }

        public Builder chankan(boolean chankan) {
            this.chankan = chankan;
            return this;
        }

        public Builder tenhou(boolean tenhou) {
            this.tenhou = tenhou;
            return this;
        }

        public Builder chiihou(boolean chiihou) {
            this.chiihou = chiihou;
            return this;
        }

        public Builder doraIndicators(List<Tile> tiles) {
            this.doraIndicators = tiles;
            return this;
        }

        public Builder uraIndicators(List<Tile> tiles) {
            this.uraIndicators = tiles;
            return this;
        }

        public Builder rules(Rules rules) {
            this.rules = rules;
            return this;
        }

        public WinContext build() {
            if (winTile == null) {
                throw new IllegalStateException("winTile is required");
            }
            return new WinContext(this);
        }
    }
}
