package com.majong.riichi.game;

import java.util.List;

/** How a hand finished and what it did to everybody's score. */
public sealed interface HandResult {

    /** Score change per seat, in seat order. */
    int[] deltas();

    /**
     * Somebody completed their hand. The scoring lines are already rendered,
     * because han and fu and tai do not share a shape.
     *
     * @param lines   one line per pattern the hand scored
     * @param summary the one line total, such as {@code 3翻40符 7700點}
     */
    record Won(int winner, int loser, List<String> lines, String summary, int[] deltas)
            implements HandResult {
        public Won {
            lines = List.copyOf(lines);
            deltas = deltas.clone();
        }

        @Override
        public int[] deltas() {
            return deltas.clone();
        }

        public boolean isTsumo() {
            return loser < 0;
        }
    }

    /** The wall ran out; ready hands collect from the others. */
    record ExhaustiveDraw(boolean[] tenpai, int[] deltas) implements HandResult {
        public ExhaustiveDraw {
            tenpai = tenpai.clone();
            deltas = deltas.clone();
        }

        @Override
        public int[] deltas() {
            return deltas.clone();
        }

        public List<Integer> tenpaiSeats() {
            List<Integer> seats = new java.util.ArrayList<>();
            for (int seat = 0; seat < tenpai.length; seat++) {
                if (tenpai[seat]) {
                    seats.add(seat);
                }
            }
            return seats;
        }
    }

    /** The hand was thrown out before anybody could finish it. */
    record AbortiveDraw(AbortReason reason, int[] deltas) implements HandResult {
        public AbortiveDraw {
            deltas = deltas.clone();
        }

        @Override
        public int[] deltas() {
            return deltas.clone();
        }
    }
}
