package io.github.sql1024.mahjong.core;

/**
 * 一個成立的役 (或寶牌) 及其飜數。役滿時 {@code han} 為役滿倍數。
 */
public record YakuValue(Yaku yaku, int han) {

    public boolean isYakuman() {
        return yaku.isYakuman();
    }

    @Override
    public String toString() {
        if (isYakuman()) {
            return yaku.chinese() + (han > 1 ? " (" + han + "倍役滿)" : " (役滿)");
        }
        return yaku.chinese() + " " + han + "飜";
    }
}
