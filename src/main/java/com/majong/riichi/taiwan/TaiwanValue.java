package com.majong.riichi.taiwan;

import java.util.List;

/**
 * What a finished taiwanese hand is worth.
 *
 * @param patterns      the tai found, in the order they are usually listed
 * @param tai           total tai
 * @param perPlayer     what one losing player pays
 * @param total         everything the winner collects
 * @param fromDiscarder paid by whoever dealt in, {@code 0} on a self draw
 */
public record TaiwanValue(List<ScoredTai> patterns, int tai, int perPlayer, int total,
                          int fromDiscarder) {

    public TaiwanValue {
        patterns = List.copyOf(patterns);
    }

    public boolean isTsumo() {
        return fromDiscarder == 0;
    }

    /** A one line summary such as {@code 5台 240點}. */
    public String summary() {
        return tai + "台 " + total + "點";
    }

    /** One scoring pattern and what it contributed. */
    public record ScoredTai(Tai pattern, int tai) {
        public String display() {
            return pattern.display() + " " + tai + "台";
        }
    }
}
