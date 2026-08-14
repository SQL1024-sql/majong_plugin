package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One player's tiles: the concealed part, the melds beside it and the tile
 * currently held after a draw.
 */
public final class Hand {

    private final List<Tile> concealed = new ArrayList<>(14);
    private final List<Meld> melds = new ArrayList<>(4);
    private Tile drawn;

    public Hand() {
    }

    public static Hand of(String notation) {
        Hand hand = new Hand();
        for (int kind : Tiles.parseAll(notation)) {
            hand.add(Tile.of(kind));
        }
        return hand;
    }

    public void add(Tile tile) {
        concealed.add(tile);
        Collections.sort(concealed);
    }

    /** Puts a freshly drawn tile aside so it can be discarded as tsumogiri. */
    public void draw(Tile tile) {
        if (drawn != null) {
            throw new IllegalStateException("a drawn tile is already held");
        }
        drawn = tile;
    }

    public Tile drawn() {
        return drawn;
    }

    /** Merges the drawn tile into the concealed part without discarding it. */
    public void keepDrawn() {
        if (drawn != null) {
            add(drawn);
            drawn = null;
        }
    }

    /**
     * Removes one tile of the given kind, preferring a non-red copy so red
     * fives stay in hand unless they are the only option.
     */
    public Tile remove(int kind) {
        keepDrawn();
        int fallback = -1;
        for (int i = 0; i < concealed.size(); i++) {
            Tile tile = concealed.get(i);
            if (tile.kind() != kind) {
                continue;
            }
            if (!tile.red()) {
                return concealed.remove(i);
            }
            fallback = i;
        }
        if (fallback < 0) {
            throw new IllegalArgumentException("hand does not hold " + Tiles.notation(kind));
        }
        return concealed.remove(fallback);
    }

    /** Removes the exact tile, red flag included. */
    public Tile removeExact(Tile tile) {
        keepDrawn();
        int index = concealed.indexOf(tile);
        if (index < 0) {
            throw new IllegalArgumentException("hand does not hold " + tile);
        }
        return concealed.remove(index);
    }

    /** Discards the drawn tile itself, leaving the concealed part untouched. */
    public Tile discardDrawn() {
        if (drawn == null) {
            throw new IllegalStateException("no drawn tile to discard");
        }
        Tile tile = drawn;
        drawn = null;
        return tile;
    }

    public void addMeld(Meld meld) {
        melds.add(meld);
    }

    public void replaceMeld(Meld previous, Meld replacement) {
        int index = melds.indexOf(previous);
        if (index < 0) {
            throw new IllegalArgumentException("meld not present: " + previous);
        }
        melds.set(index, replacement);
    }

    public List<Tile> concealed() {
        return Collections.unmodifiableList(concealed);
    }

    /** Concealed tiles plus the drawn tile, sorted. */
    public List<Tile> allConcealed() {
        List<Tile> all = new ArrayList<>(concealed);
        if (drawn != null) {
            all.add(drawn);
            Collections.sort(all);
        }
        return all;
    }

    public List<Meld> melds() {
        return Collections.unmodifiableList(melds);
    }

    public boolean isClosed() {
        return melds.stream().noneMatch(Meld::isOpen);
    }

    public int kanCount() {
        return (int) melds.stream().filter(Meld::isKan).count();
    }

    public int count(int kind) {
        int count = 0;
        for (Tile tile : concealed) {
            if (tile.kind() == kind) {
                count++;
            }
        }
        if (drawn != null && drawn.kind() == kind) {
            count++;
        }
        return count;
    }

    /** Counts of the concealed part only, excluding the drawn tile. */
    public int[] counts() {
        int[] counts = new int[Tiles.KINDS];
        for (Tile tile : concealed) {
            counts[tile.kind()]++;
        }
        return counts;
    }

    /** Counts of the concealed part including the drawn tile. */
    public int[] countsWithDrawn() {
        int[] counts = counts();
        if (drawn != null) {
            counts[drawn.kind()]++;
        }
        return counts;
    }

    /** Every tile the hand owns, melds included. */
    public List<Tile> allTiles() {
        List<Tile> all = new ArrayList<>(allConcealed());
        for (Meld meld : melds) {
            all.addAll(meld.tiles());
        }
        return all;
    }

    public String display() {
        StringBuilder builder = new StringBuilder();
        for (Tile tile : concealed) {
            builder.append(tile.display()).append(' ');
        }
        if (drawn != null) {
            builder.append("| ").append(drawn.display()).append(' ');
        }
        for (Meld meld : melds) {
            builder.append("  ").append(meld.display());
        }
        return builder.toString().trim();
    }
}
