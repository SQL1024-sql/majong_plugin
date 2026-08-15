package com.majong.riichi.core;

import java.util.List;

/**
 * The full result of scoring a finished hand.
 *
 * @param yaku         the patterns found, in the order they are usually listed
 * @param han          total han, dora included
 * @param fu           fu after rounding up
 * @param dora         han from dora indicators
 * @param uraDora      han from the indicators revealed under a riichi
 * @param redDora      han from red fives
 * @param basePoints   points before the dealer and self draw multipliers
 * @param limitName    the limit reached, or {@code null} below mangan
 * @param payment      who pays what
 */
public record HandValue(List<ScoredYaku> yaku, int han, int fu, int dora, int uraDora, int redDora,
                        int basePoints, String limitName, Payment payment) {

    public HandValue {
        yaku = List.copyOf(yaku);
    }

    public boolean isYakuman() {
        return yaku.stream().anyMatch(scored -> scored.yaku().isYakuman());
    }

    /** A one line summary such as {@code 3翻40符 7700点}. */
    public String summary() {
        StringBuilder builder = new StringBuilder();
        if (isYakuman()) {
            builder.append("役満");
        } else {
            builder.append(han).append("翻").append(fu).append("符");
            if (limitName != null) {
                builder.append(' ').append(limitName);
            }
        }
        builder.append(' ').append(payment.total()).append("点");
        return builder.toString();
    }
}
