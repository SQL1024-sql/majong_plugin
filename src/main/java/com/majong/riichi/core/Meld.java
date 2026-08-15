package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A called (or declared) group of tiles sitting face up beside a hand.
 *
 * @param type       how the group was formed
 * @param tiles      the tiles making up the group, low to high
 * @param calledTile the tile taken from an opponent, or {@code null} for a closed kan
 * @param fromSeat   absolute seat the tile was claimed from, or {@code -1} for a closed kan
 */
public record Meld(MeldType type, List<Tile> tiles, Tile calledTile, int fromSeat) {

    public Meld {
        tiles = List.copyOf(tiles);
        if (tiles.size() != type.size()) {
            throw new IllegalArgumentException(type + " needs " + type.size() + " tiles, got " + tiles.size());
        }
        if (type == MeldType.ANKAN) {
            if (calledTile != null || fromSeat >= 0) {
                throw new IllegalArgumentException("a closed kan is not claimed from anyone");
            }
        } else if (calledTile == null || fromSeat < 0) {
            throw new IllegalArgumentException(type + " must record the tile it claimed");
        }
    }

    public static Meld chi(List<Tile> tiles, Tile calledTile, int fromSeat) {
        return new Meld(MeldType.CHI, sorted(tiles), calledTile, fromSeat);
    }

    public static Meld pon(List<Tile> tiles, Tile calledTile, int fromSeat) {
        return new Meld(MeldType.PON, sorted(tiles), calledTile, fromSeat);
    }

    public static Meld ankan(List<Tile> tiles) {
        return new Meld(MeldType.ANKAN, sorted(tiles), null, -1);
    }

    public static Meld daiminkan(List<Tile> tiles, Tile calledTile, int fromSeat) {
        return new Meld(MeldType.DAIMINKAN, sorted(tiles), calledTile, fromSeat);
    }

    /** Upgrades an existing pon into an open kan with the fourth tile. */
    public Meld upgradeToKan(Tile fourth) {
        if (type != MeldType.PON) {
            throw new IllegalStateException("only a pon can be upgraded to a kan");
        }
        List<Tile> all = new ArrayList<>(tiles);
        all.add(fourth);
        return new Meld(MeldType.SHOUMINKAN, sorted(all), calledTile, fromSeat);
    }

    public boolean isKan() {
        return type.isKan();
    }

    public boolean isOpen() {
        return type.isOpen();
    }

    /** True for a triplet or kan of a single kind. */
    public boolean isTripletLike() {
        return type != MeldType.CHI;
    }

    /** The kind this meld is built on; for a chi, the lowest tile of the run. */
    public int baseKind() {
        return tiles.getFirst().kind();
    }

    public int redCount() {
        return (int) tiles.stream().filter(Tile::red).count();
    }

    /** Every tile kind in the meld, with kan counted as three for scoring shape purposes. */
    public List<Integer> kinds() {
        return tiles.stream().map(Tile::kind).toList();
    }

    public String display() {
        StringBuilder builder = new StringBuilder();
        for (Tile tile : tiles) {
            builder.append(tile.display());
        }
        if (type == MeldType.ANKAN) {
            builder.insert(0, '[').append(']');
        }
        return builder.toString();
    }

    private static List<Tile> sorted(List<Tile> tiles) {
        List<Tile> copy = new ArrayList<>(tiles);
        copy.sort(Comparator.naturalOrder());
        return copy;
    }
}
