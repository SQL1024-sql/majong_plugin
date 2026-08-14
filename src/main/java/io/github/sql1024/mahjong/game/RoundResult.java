package io.github.sql1024.mahjong.game;

import java.util.List;

/**
 * 一局結束的結果。
 *
 * @param type          結束方式
 * @param wins          和牌內容 (流局時為空)
 * @param tenpaiSeats   流局時的聽牌者
 * @param deltas        四家的點數增減
 * @param reason        說明文字 (流局原因等)
 * @param dealerKeeps   莊家是否連莊
 * @param gameOver      整場對局是否結束
 */
public record RoundResult(Type type, List<WinEntry> wins, List<Integer> tenpaiSeats, int[] deltas,
                          String reason, boolean dealerKeeps, boolean gameOver) {

    public enum Type {
        /** 自摸 */
        TSUMO,
        /** 榮和 */
        RON,
        /** 荒牌流局 */
        EXHAUSTIVE_DRAW,
        /** 途中流局 */
        ABORTIVE_DRAW
    }

    public RoundResult {
        wins = List.copyOf(wins);
        tenpaiSeats = List.copyOf(tenpaiSeats);
        deltas = deltas.clone();
    }

    @Override
    public int[] deltas() {
        return deltas.clone();
    }

    public boolean isDraw() {
        return type == Type.EXHAUSTIVE_DRAW || type == Type.ABORTIVE_DRAW;
    }
}
