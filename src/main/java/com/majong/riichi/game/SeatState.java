package com.majong.riichi.game;

import com.majong.riichi.core.Flower;
import com.majong.riichi.core.Hand;
import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.core.WinChecker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** One player's position at the table for the duration of a hand. */
public final class SeatState {

    private final int seat;
    private final String name;
    private final int totalSets;
    private int score;
    /** Flowers turned face up beside the hand; taiwanese play only. */
    private final List<Flower> flowers = new ArrayList<>();

    private Hand hand = new Hand();
    private final List<Tile> discards = new ArrayList<>(24);
    /** Indices into {@link #discards} where a riichi was declared. */
    private int riichiDiscardIndex = -1;

    private boolean riichi;
    private boolean doubleRiichi;
    private boolean ippatsu;
    private boolean temporaryFuriten;
    private boolean riichiFuriten;
    private boolean drewThisHand;

    SeatState(int seat, String name, int score, int totalSets) {
        this.seat = seat;
        this.name = name;
        this.score = score;
        this.totalSets = totalSets;
    }

    void resetForHand() {
        hand = new Hand();
        flowers.clear();
        discards.clear();
        riichiDiscardIndex = -1;
        riichi = false;
        doubleRiichi = false;
        ippatsu = false;
        temporaryFuriten = false;
        riichiFuriten = false;
        drewThisHand = false;
    }

    public int seat() {
        return seat;
    }

    public String name() {
        return name;
    }

    public int score() {
        return score;
    }

    void addScore(int delta) {
        score += delta;
    }

    public Hand hand() {
        return hand;
    }

    public List<Tile> discards() {
        return Collections.unmodifiableList(discards);
    }

    void addDiscard(Tile tile) {
        discards.add(tile);
    }

    /** Removes the last discard, used when an opponent claims it. */
    Tile takeLastDiscard() {
        return discards.removeLast();
    }

    public boolean riichi() {
        return riichi;
    }

    public boolean doubleRiichi() {
        return doubleRiichi;
    }

    void declareRiichi(boolean isDouble) {
        riichi = true;
        doubleRiichi = isDouble;
        ippatsu = true;
        riichiDiscardIndex = discards.size();
    }

    public int riichiDiscardIndex() {
        return riichiDiscardIndex;
    }

    public boolean ippatsu() {
        return ippatsu;
    }

    void clearIppatsu() {
        ippatsu = false;
    }

    public boolean hasDrawnThisHand() {
        return drewThisHand;
    }

    void markDrawn() {
        drewThisHand = true;
    }

    void setTemporaryFuriten(boolean value) {
        temporaryFuriten = value;
    }

    void setRiichiFuriten() {
        riichiFuriten = true;
    }

    public List<Flower> flowers() {
        return Collections.unmodifiableList(flowers);
    }

    void addFlower(Flower flower) {
        flowers.add(flower);
    }

    /** The tiles this hand is waiting on, ignoring the tile currently drawn. */
    public Set<Integer> waits() {
        return WinChecker.waits(hand.counts(), hand.melds().size(), totalSets);
    }

    /**
     * A player who has already let one of their winning tiles go by may not
     * claim a discard, only win by self draw.
     */
    public boolean isFuriten() {
        if (temporaryFuriten || riichiFuriten) {
            return true;
        }
        Set<Integer> waits = waits();
        if (waits.isEmpty()) {
            return false;
        }
        for (Tile discard : discards) {
            if (waits.contains(discard.kind())) {
                return true;
            }
        }
        return false;
    }

    public boolean isTenpai() {
        return !waits().isEmpty();
    }

    /** True when the hand holds nine or more distinct terminals and honours. */
    public boolean hasNineTerminals() {
        int[] counts = hand.countsWithDrawn();
        int distinct = 0;
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            if (counts[kind] > 0 && Tiles.isTerminalOrHonor(kind)) {
                distinct++;
            }
        }
        return distinct >= 9;
    }
}
