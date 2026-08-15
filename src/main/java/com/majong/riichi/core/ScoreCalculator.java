package com.majong.riichi.core;

/**
 * Turns han and fu into base points, and base points into what each player pays.
 */
public final class ScoreCalculator {

    public static final int YAKUMAN_BASE = 8000;

    private ScoreCalculator() {
    }

    /**
     * Base points for a hand, before the dealer and self draw multipliers. The
     * usual limits apply, and thirteen or more han counts as a yakuman.
     */
    public static int basePoints(int han, int fu) {
        if (han >= 13) {
            return YAKUMAN_BASE;
        }
        if (han >= 11) {
            return 6000;
        }
        if (han >= 8) {
            return 4000;
        }
        if (han >= 6) {
            return 3000;
        }
        if (han == 5) {
            return 2000;
        }
        int base = fu * (1 << (2 + han));
        return Math.min(base, 2000);
    }

    /** The japanese name for the limit a hand reached, or {@code null} below mangan. */
    public static String limitName(int han, int fu) {
        if (han >= 13) {
            return "数え役満";
        }
        if (han >= 11) {
            return "三倍満";
        }
        if (han >= 8) {
            return "倍満";
        }
        if (han >= 6) {
            return "跳満";
        }
        if (han == 5 || fu * (1 << (2 + han)) >= 2000) {
            return "満貫";
        }
        return null;
    }

    public static Payment payment(int base, boolean dealer, boolean tsumo, int honba, int riichiSticks) {
        int honbaPoints = 300 * honba;
        int stickPoints = 1000 * riichiSticks;
        if (!tsumo) {
            int fromDiscarder = roundUp100(base * (dealer ? 6 : 4)) + honbaPoints;
            return new Payment(fromDiscarder + stickPoints, fromDiscarder, 0, 0, honbaPoints, stickPoints);
        }
        int perHonba = 100 * honba;
        if (dealer) {
            int each = roundUp100(base * 2) + perHonba;
            int total = each * 3 + stickPoints;
            return new Payment(total, 0, 0, each, perHonba * 3, stickPoints);
        }
        int fromDealer = roundUp100(base * 2) + perHonba;
        int fromOther = roundUp100(base) + perHonba;
        int total = fromDealer + fromOther * 2 + stickPoints;
        return new Payment(total, 0, fromDealer, fromOther, perHonba * 3, stickPoints);
    }

    /** Points paid by each player still noten when the wall runs out. */
    public static int[] exhaustiveDrawTransfer(boolean[] tenpai) {
        int[] deltas = new int[tenpai.length];
        int ready = 0;
        for (boolean value : tenpai) {
            if (value) {
                ready++;
            }
        }
        if (ready == 0 || ready == tenpai.length) {
            return deltas;
        }
        int gain = 3000 / ready;
        int loss = 3000 / (tenpai.length - ready);
        for (int seat = 0; seat < tenpai.length; seat++) {
            deltas[seat] = tenpai[seat] ? gain : -loss;
        }
        return deltas;
    }

    private static int roundUp100(int points) {
        return (points + 99) / 100 * 100;
    }
}
