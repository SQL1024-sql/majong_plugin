package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.Tile;

import java.util.List;

/**
 * 一個可以宣告的選項。
 *
 * @param type      動作
 * @param handTiles 需要從手牌拿出來的牌 (榮/自摸/立直為空)
 * @param target    被鳴的牌 (暗槓/加槓為要槓的牌)
 */
public record CallOption(CallType type, List<Tile> handTiles, Tile target) {

    public CallOption {
        handTiles = List.copyOf(handTiles);
    }

    public static CallOption of(CallType type) {
        return new CallOption(type, List.of(), null);
    }

    /** 顯示用的簡短說明，例如「吃 4m5m」。 */
    public String describe() {
        if (handTiles.isEmpty()) {
            return type.displayName() + (target == null ? "" : " " + target.displayName());
        }
        StringBuilder sb = new StringBuilder(type.displayName()).append(' ');
        for (Tile tile : handTiles) {
            sb.append(tile.displayName());
        }
        return sb.toString();
    }
}
