package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 牌山。
 *
 * <p>共 136 張，其中 14 張為王牌 (嶺上牌 4 張、表寶牌指示牌 5 張、裏寶牌指示牌 5 張)。
 */
public final class Wall {

    /** 王牌張數。 */
    public static final int DEAD_WALL_SIZE = 14;
    /** 最多可開的槓數。 */
    public static final int MAX_KAN = 4;

    private final List<Tile> tiles;
    private final List<Tile> deadWall;
    private int drawIndex;
    private int liveEnd;
    private int revealedDora = 1;
    private int kanCount;

    public Wall(Random random, int akaCount) {
        this.tiles = buildTiles(akaCount);
        Collections.shuffle(this.tiles, random);
        this.deadWall = new ArrayList<>(this.tiles.subList(this.tiles.size() - DEAD_WALL_SIZE, this.tiles.size()));
        this.liveEnd = this.tiles.size() - DEAD_WALL_SIZE;
    }

    private static List<Tile> buildTiles(int akaCount) {
        List<Tile> all = new ArrayList<>(136);
        for (int id = 0; id < Tile.KINDS; id++) {
            for (int copy = 0; copy < 4; copy++) {
                all.add(Tile.of(id));
            }
        }
        int[] fives = {Tile.idOf(Suit.MAN, 5), Tile.idOf(Suit.PIN, 5), Tile.idOf(Suit.SOU, 5), Tile.idOf(Suit.PIN, 5)};
        int replaced = 0;
        for (int i = 0; i < fives.length && replaced < akaCount; i++, replaced++) {
            int target = fives[i];
            for (int index = 0; index < all.size(); index++) {
                if (all.get(index).id() == target && !all.get(index).isRed()) {
                    all.set(index, Tile.red(target));
                    break;
                }
            }
        }
        return all;
    }

    /** 從牌山摸一張；牌山已盡回傳 {@code null}。 */
    public Tile draw() {
        if (drawIndex >= liveEnd) {
            return null;
        }
        return tiles.get(drawIndex++);
    }

    /** 開槓後摸嶺上牌。 */
    public Tile drawReplacement() {
        if (kanCount >= MAX_KAN) {
            throw new IllegalStateException("No replacement tiles left");
        }
        Tile tile = deadWall.get(DEAD_WALL_SIZE - 1 - kanCount);
        kanCount++;
        liveEnd--;
        return tile;
    }

    /** 翻開新的寶牌指示牌 (開槓時)。 */
    public void revealNewDora() {
        if (revealedDora < 5) {
            revealedDora++;
        }
    }

    public List<Tile> doraIndicators() {
        return List.copyOf(deadWall.subList(0, revealedDora));
    }

    public List<Tile> uraIndicators() {
        return List.copyOf(deadWall.subList(5, 5 + revealedDora));
    }

    /** 剩餘可摸張數。 */
    public int remaining() {
        return Math.max(0, liveEnd - drawIndex);
    }

    public boolean isExhausted() {
        return remaining() <= 0;
    }

    public int kanCount() {
        return kanCount;
    }

    public boolean canKan() {
        return kanCount < MAX_KAN;
    }

    /** 除錯用：取得整個牌山的內容 (不可變)。 */
    public List<Tile> allTiles() {
        return List.copyOf(tiles);
    }
}
