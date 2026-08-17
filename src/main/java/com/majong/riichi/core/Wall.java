package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The 136 tile wall, including the 14 tile dead wall that supplies replacement
 * tiles for kans and the dora indicators.
 */
public final class Wall {

    private static final int DEAD_WALL_SIZE = 14;
    private static final int MAX_KANS = 4;

    private final List<Piece> pieces;
    private final List<Tile> tiles;
    /** Japanese walls reserve fourteen tiles for kans and dora; taiwanese ones do not. */
    private final int deadWallSize;
    /** Index of the next tile to be drawn from the live wall. */
    private int drawIndex;
    /** Exclusive end of the live wall; shrinks by one for every replacement draw. */
    private int liveEnd;
    private int kansDeclared;
    private int revealedIndicators = 1;

    private Wall(List<Piece> pieces, int deadWallSize) {
        this.pieces = pieces;
        this.deadWallSize = deadWallSize;
        // The dora machinery only ever looks at ordinary tiles, and only a
        // japanese wall has a dead wall for it to look at.
        this.tiles = pieces.stream().map(Piece::tile).toList();
        this.drawIndex = 0;
        this.liveEnd = pieces.size() - deadWallSize;
    }

    /** Builds a shuffled wall; with {@code redFives} one five of each suit is red. */
    public static Wall shuffled(Random random, boolean redFives) {
        List<Piece> pieces = new ArrayList<>(136);
        for (Tile tile : fullSet(redFives)) {
            pieces.add(Piece.of(tile));
        }
        Collections.shuffle(pieces, random);
        return new Wall(pieces, DEAD_WALL_SIZE);
    }

    /**
     * Builds the 144 piece wall a taiwanese set uses: the same tiles plus the
     * eight flowers, and no dead wall, since replacements come off the back.
     */
    public static Wall shuffledWithFlowers(Random random) {
        List<Piece> pieces = new ArrayList<>(144);
        for (Tile tile : fullSet(false)) {
            pieces.add(Piece.of(tile));
        }
        for (Flower flower : Flower.all()) {
            pieces.add(Piece.of(flower));
        }
        Collections.shuffle(pieces, random);
        return new Wall(pieces, 0);
    }

    private static List<Tile> fullSet(boolean redFives) {
        List<Tile> tiles = new ArrayList<>(136);
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            boolean canBeRed = redFives && !Tiles.isHonor(kind) && Tiles.rankOf(kind) == 5;
            for (int copy = 0; copy < 4; copy++) {
                tiles.add(new Tile(kind, canBeRed && copy == 0));
            }
        }
        return tiles;
    }

    /** Builds a wall in the given order, for tests and replays. */
    public static Wall ordered(List<Tile> tiles) {
        if (tiles.size() != 136) {
            throw new IllegalArgumentException("a wall holds 136 tiles, got " + tiles.size());
        }
        List<Piece> pieces = new ArrayList<>(136);
        for (Tile tile : tiles) {
            pieces.add(Piece.of(tile));
        }
        return new Wall(pieces, DEAD_WALL_SIZE);
    }

    /** True when this wall mixes flowers in with the tiles. */
    public boolean hasFlowers() {
        return deadWallSize == 0;
    }

    /** Tiles still drawable from the live wall. */
    public int remaining() {
        return liveEnd - drawIndex;
    }

    public boolean isExhausted() {
        return remaining() <= 0;
    }

    public Tile draw() {
        Piece piece = drawPiece();
        if (piece.isFlower()) {
            throw new IllegalStateException("this wall deals flowers; use drawPiece");
        }
        return piece.tile();
    }

    /** Draws whatever comes next, which on a taiwanese wall may be a flower. */
    public Piece drawPiece() {
        if (isExhausted()) {
            throw new IllegalStateException("the live wall is empty");
        }
        return pieces.get(drawIndex++);
    }

    public boolean canDeclareKan() {
        return (hasFlowers() || kansDeclared < MAX_KANS) && !isExhausted();
    }

    public int kansDeclared() {
        return kansDeclared;
    }

    /**
     * Takes a replacement tile from the dead wall after a kan. The live wall
     * gives up its last tile so the dead wall stays 14 tiles long.
     */
    public Tile drawReplacement() {
        Piece piece = drawReplacementPiece();
        if (piece.isFlower()) {
            throw new IllegalStateException("this wall deals flowers; use drawReplacementPiece");
        }
        return piece.tile();
    }

    /**
     * Takes a replacement from the back of the wall, for a kan or for a flower.
     * A japanese wall keeps its dead wall the same length by giving up the last
     * live tile; a taiwanese one simply works backwards.
     */
    public Piece drawReplacementPiece() {
        if (hasFlowers()) {
            if (liveEnd <= drawIndex) {
                throw new IllegalStateException("the wall is empty");
            }
            return pieces.get(--liveEnd);
        }
        if (kansDeclared >= MAX_KANS) {
            throw new IllegalStateException("a hand allows at most four kans");
        }
        Piece replacement = pieces.get(pieces.size() - 1 - kansDeclared);
        kansDeclared++;
        liveEnd--;
        return replacement;
    }

    /** Flips the next dora indicator; called after a kan. */
    public void revealKanIndicator() {
        if (hasFlowers()) {
            return;
        }
        if (revealedIndicators >= 5) {
            throw new IllegalStateException("all dora indicators are already revealed");
        }
        revealedIndicators++;
    }

    public List<Tile> doraIndicators() {
        if (hasFlowers()) {
            // A taiwanese wall has no dead wall, and the rules have no dora.
            return List.of();
        }
        List<Tile> indicators = new ArrayList<>(revealedIndicators);
        for (int i = 0; i < revealedIndicators; i++) {
            indicators.add(tiles.get(indicatorIndex(i)));
        }
        return indicators;
    }

    public List<Tile> uraIndicators() {
        if (hasFlowers()) {
            return List.of();
        }
        List<Tile> indicators = new ArrayList<>(revealedIndicators);
        for (int i = 0; i < revealedIndicators; i++) {
            indicators.add(tiles.get(indicatorIndex(i) + 1));
        }
        return indicators;
    }

    /** Counts how much dora the given tiles are worth, red fives included. */
    public int countDora(List<Tile> tiles, boolean includeUra) {
        int dora = 0;
        for (Tile tile : tiles) {
            if (tile.red()) {
                dora++;
            }
            for (Tile indicator : doraIndicators()) {
                if (tile.kind() == Tiles.doraFromIndicator(indicator.kind())) {
                    dora++;
                }
            }
            if (includeUra) {
                for (Tile indicator : uraIndicators()) {
                    if (tile.kind() == Tiles.doraFromIndicator(indicator.kind())) {
                        dora++;
                    }
                }
            }
        }
        return dora;
    }

    /**
     * Dead wall layout, counting back from the end: the four replacement tiles
     * sit at the very back, the five indicator pairs in front of them.
     */
    private int indicatorIndex(int nth) {
        return tiles.size() - DEAD_WALL_SIZE + 2 * nth;
    }
}
