package io.github.sql1024.mahjong.core;

/** 由基本點換算實際支付點數。 */
public final class ScoreCalculator {

    /** 自摸的支付明細。 */
    public record TsumoPayment(int fromDealer, int fromNonDealer, int total) {
    }

    private ScoreCalculator() {
    }

    /** 進位到百位。 */
    public static int roundUp100(int points) {
        return ((points + 99) / 100) * 100;
    }

    /**
     * 榮和時放銃者要支付的點數 (含本場)。
     *
     * @param basePoints 基本點
     * @param dealerWin  和牌者是否為莊家
     * @param honba      本場數
     */
    public static int ronPayment(int basePoints, boolean dealerWin, int honba) {
        int points = roundUp100(basePoints * (dealerWin ? 6 : 4));
        return points + honba * 300;
    }

    /**
     * 自摸時各家要支付的點數 (含本場)。
     */
    public static TsumoPayment tsumoPayment(int basePoints, boolean dealerWin, int honba) {
        if (dealerWin) {
            int each = roundUp100(basePoints * 2) + honba * 100;
            return new TsumoPayment(0, each, each * 3);
        }
        int fromDealer = roundUp100(basePoints * 2) + honba * 100;
        int fromOthers = roundUp100(basePoints) + honba * 100;
        return new TsumoPayment(fromDealer, fromOthers, fromDealer + fromOthers * 2);
    }
}
