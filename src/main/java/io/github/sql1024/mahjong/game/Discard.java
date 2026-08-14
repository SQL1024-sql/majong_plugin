package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.Tile;

/**
 * 牌河中的一張牌。
 *
 * @param tile           牌
 * @param riichiDeclare  是否為立直宣言牌 (橫放)
 * @param tsumogiri      是否為摸切
 */
public final class Discard {

    private final Tile tile;
    private final boolean riichiDeclare;
    private final boolean tsumogiri;
    private boolean called;

    public Discard(Tile tile, boolean riichiDeclare, boolean tsumogiri) {
        this.tile = tile;
        this.riichiDeclare = riichiDeclare;
        this.tsumogiri = tsumogiri;
    }

    public Tile tile() {
        return tile;
    }

    public boolean riichiDeclare() {
        return riichiDeclare;
    }

    public boolean tsumogiri() {
        return tsumogiri;
    }

    /** 是否已被其他人鳴走。 */
    public boolean called() {
        return called;
    }

    public void markCalled() {
        this.called = true;
    }
}
