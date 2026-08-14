package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.Tile;

import java.util.List;

/**
 * 輪到自己時可以做的事。
 *
 * @param canTsumo       是否可以自摸
 * @param riichiDiscards 若可以立直，這些是能保持聽牌的打牌
 * @param kanOptions     可以宣告的暗槓 / 加槓
 * @param canKyuushu     是否可以宣告九種九牌
 */
public record TurnOptions(boolean canTsumo, List<Tile> riichiDiscards, List<CallOption> kanOptions,
                          boolean canKyuushu) {

    public TurnOptions {
        riichiDiscards = List.copyOf(riichiDiscards);
        kanOptions = List.copyOf(kanOptions);
    }

    public boolean canRiichi() {
        return !riichiDiscards.isEmpty();
    }

    public boolean isEmpty() {
        return !canTsumo && riichiDiscards.isEmpty() && kanOptions.isEmpty() && !canKyuushu;
    }
}
