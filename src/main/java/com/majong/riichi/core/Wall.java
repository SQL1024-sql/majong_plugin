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

    private final List<Tile> tiles;
    /** Index of the next tile to be drawn from the live wall. */
    private int drawIndex;
    /** Exclusive end of the live wall; shrinks by one for every replacement draw. */
    private int liveEnd;
    private int kansDeclared;
    private int revealedIndicators = 1;

    private Wall(List<Tile> tiles) {
        this.tiles = tiles;
        this.drawIndex = 0;
        this.liveEnd = tiles.size() - DEAD_WALL_SIZE;
    }

    /** Builds a shuffled wall; with {@code redFives} one five of each suit is red. */
    public static Wall shuffled(Random random, boolean redFives) {
        List<Tile> tiles = new ArrayList<>(136);
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            boolean canBeRed = redFives && !Tiles.isHonor(kind) && Tiles.rankOf(kind) == 5;
            for (int copy = 0; copy < 4; copy++) {
                tiles.add(new Tile(kind, canBeRed && copy == 0));
            }
        }
        Collections.shuffle(tiles, random);
        return new Wall(tiles);
    }

    /** Builds a wall in the given order, for tests and replays. */
    public static Wall ordered(List<Tile> tiles) {
        if (tiles.size() != 136) {
            throw new IllegalArgumentException("a wall holds 136 tiles, got " + tiles.size());
        }
        return new Wall(new ArrayList<>(tiles));
    }

    /** Tiles still drawable from the live wall. */
    public int remaining() {
        return liveEnd - drawIndex;
    }

    public boolean isExhausted() {
        return remaining() <= 0;
    }

    public Tile draw() {
        if (isExhausted()) {
            throw new IllegalStateException("the live wall is empty");
        }
        return tiles.get(drawIndex++);
    }

    public boolean canDeclareKan() {
        return kansDeclared < MAX_KANS && !isExhausted();
    }

    public int kansDeclared() {
        return kansDeclared;
    }

    /**
     * Takes a replacement tile from the dead wall after a kan. The live wall
     * gives up its last tile so the dead wall stays 14 tiles long.
     */
    public Tile drawReplacement() {
        if (kansDeclared >= MAX_KANS) {
            throw new IllegalStateException("a hand allows at most four kans");
        }
        Tile replacement = tiles.get(tiles.size() - 1 - kansDeclared);
        kansDeclared++;
        liveEnd--;
        return replacement;
    }

    /** Flips the next dora indicator; called after a kan. */
    public void revealKanIndicator() {
        if (revealedIndicators >= 5) {
            throw new IllegalStateException("all dora indicators are already revealed");
        }
        revealedIndicators++;
    }

    public List<Tile> doraIndicators() {
        List<Tile> indicators = new ArrayList<>(revealedIndicators);
        for (int i = 0; i < revealedIndicators; i++) {
            indicators.add(tiles.get(indicatorIndex(i)));
        }
        return indicators;
    }

    public List<Tile> uraIndicators() {
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
