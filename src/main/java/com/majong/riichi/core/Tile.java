package com.majong.riichi.core;

/**
 * A physical tile: one of the 34 kinds, optionally the red five of its suit.
 */
public record Tile(int kind, boolean red) implements Comparable<Tile> {

    public Tile {
        if (kind < 0 || kind >= Tiles.KINDS) {
            throw new IllegalArgumentException("tile kind out of range: " + kind);
        }
        if (red && (Tiles.isHonor(kind) || Tiles.rankOf(kind) != 5)) {
            throw new IllegalArgumentException("only fives can be red: " + kind);
        }
    }

    public static Tile of(int kind) {
        return new Tile(kind, false);
    }

    public static Tile red(Suit suit) {
        return new Tile(Tiles.kindOf(suit, 5), true);
    }

    public static Tile parse(String text) {
        String trimmed = text.trim();
        boolean red = trimmed.length() == 2 && trimmed.charAt(0) == '0';
        return new Tile(Tiles.parse(trimmed), red);
    }

    public Suit suit() {
        return Tiles.suitOf(kind);
    }

    public int rank() {
        return Tiles.rankOf(kind);
    }

    public boolean isHonor() {
        return Tiles.isHonor(kind);
    }

    public boolean isTerminalOrHonor() {
        return Tiles.isTerminalOrHonor(kind);
    }

    /** Short notation, using {@code 0} for red fives. */
    public String notation() {
        if (red) {
            return "0" + suit().code();
        }
        return Tiles.notation(kind);
    }

    /** Display name, marking red fives with a trailing {@code *}. */
    public String display() {
        return Tiles.display(kind) + (red ? "*" : "");
    }

    @Override
    public int compareTo(Tile other) {
        if (kind != other.kind) {
            return Integer.compare(kind, other.kind);
        }
        return Boolean.compare(other.red, red);
    }

    @Override
    public String toString() {
        return notation();
    }
}
