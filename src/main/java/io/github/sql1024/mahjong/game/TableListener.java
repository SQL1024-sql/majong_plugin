package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.Tile;

import java.util.List;

/** 牌桌事件的接收者；由介面層 (聊天 / GUI) 或 AI 實作。 */
public interface TableListener {

    /** 新的一局開始，手牌已配完。 */
    default void onRoundStart(MahjongTable table) {
    }

    /** 有人摸牌。 */
    default void onDraw(MahjongTable table, Seat seat, Tile tile, boolean replacement) {
    }

    /** 輪到某人行動 (需要打牌)。 */
    default void onTurnStart(MahjongTable table, Seat seat, TurnOptions options) {
    }

    /** 有人打出一張牌。 */
    default void onDiscard(MahjongTable table, Seat seat, Tile tile, boolean riichiDeclaration) {
    }

    /** 某人可以鳴牌 / 榮和。 */
    default void onCallOptions(MahjongTable table, Seat seat, List<CallOption> options, Tile target, Seat from) {
    }

    /** 有人完成鳴牌。 */
    default void onCall(MahjongTable table, Seat seat, Meld meld) {
    }

    /** 有人宣告立直。 */
    default void onRiichi(MahjongTable table, Seat seat) {
    }

    /** 翻開新的寶牌指示牌。 */
    default void onNewDora(MahjongTable table, Tile indicator) {
    }

    /** 一局結束。 */
    default void onRoundEnd(MahjongTable table, RoundResult result) {
    }

    /** 整場對局結束。 */
    default void onGameEnd(MahjongTable table) {
    }

    /** 一般訊息。 */
    default void onInfo(MahjongTable table, String message) {
    }
}
