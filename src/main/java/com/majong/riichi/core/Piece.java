package com.majong.riichi.core;

/**
 * Something drawn from the wall. A japanese set holds nothing but tiles; a
 * taiwanese one mixes in the eight flowers, which are set aside rather than
 * kept in hand.
 */
public sealed interface Piece {

    record OfTile(Tile tile) implements Piece {
    }

    record OfFlower(Flower flower) implements Piece {
    }

    static Piece of(Tile tile) {
        return new OfTile(tile);
    }

    static Piece of(Flower flower) {
        return new OfFlower(flower);
    }

    default boolean isFlower() {
        return this instanceof OfFlower;
    }

    /** The tile drawn, or {@code null} when a flower came up. */
    default Tile tile() {
        return this instanceof OfTile drawn ? drawn.tile() : null;
    }

    /** The flower drawn, or {@code null} when an ordinary tile came up. */
    default Flower flower() {
        return this instanceof OfFlower drawn ? drawn.flower() : null;
    }
}
