package io.github.sql1024.mahjong.core;

/** 風位 (座位風 / 場風)。 */
public enum Wind {
    EAST("東"),
    SOUTH("南"),
    WEST("西"),
    NORTH("北");

    private final String displayName;

    Wind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 對應的字牌 id (27..30)。 */
    public int tileId() {
        return Tile.EAST + ordinal();
    }

    public Tile tile() {
        return Tile.of(tileId());
    }

    public Wind next() {
        return values()[(ordinal() + 1) % 4];
    }

    /** 以 {@code steps} 個座位之後的風位。 */
    public Wind shift(int steps) {
        return values()[Math.floorMod(ordinal() + steps, 4)];
    }

    public static Wind fromTileId(int tileId) {
        if (tileId < Tile.EAST || tileId > Tile.NORTH) {
            throw new IllegalArgumentException("Not a wind tile: " + tileId);
        }
        return values()[tileId - Tile.EAST];
    }
}
