package com.majong.riichi.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.majong.riichi.bot.SimpleBot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaiwaneseGameTest {

    private static final List<String> NAMES = List.of("east", "south", "west", "north");
    private static final int STEP_LIMIT = 100_000;

    /**
     * An east round only game. Sixteen tile hands make the bots' shanten search
     * markedly heavier, so the test plays the short game rather than the long one.
     */
    private static GameRules shortGame() {
        return new GameRules(Variant.TAIWANESE, 0, com.majong.riichi.core.Tiles.EAST,
                false, true, true, false);
    }

    private static int seatToAct(RiichiGame game) {
        if (game.phase() == RiichiGame.Phase.ACT) {
            return game.currentSeat();
        }
        for (int seat : game.pendingSeats()) {
            if (!game.options(seat).isEmpty()) {
                return seat;
            }
        }
        throw new IllegalStateException("the table is waiting on nobody");
    }

    /** Points only move between players, so the table always sums to zero. */
    private static void assertPointsConserved(RiichiGame game) {
        int total = 0;
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            total += game.seat(seat).score();
        }
        assertEquals(0, total);
    }

    @Test
    void dealsSixteenTilesToEverybody() {
        RiichiGame game = new RiichiGame(GameRules.taiwanese(), NAMES, 1L, null);
        game.startHand();

        assertEquals(Variant.TAIWANESE, game.variant());
        assertEquals(17, game.seat(0).hand().allConcealed().size());
        for (int seat = 1; seat < RiichiGame.SEATS; seat++) {
            assertEquals(16, game.seat(seat).hand().allConcealed().size());
        }
        // A taiwanese wall has no dead wall and so no dora indicators.
        assertTrue(game.doraIndicators().isEmpty());
    }

    @Test
    void flowersAreSetAsideAndReplaced() {
        // Over a few deals somebody is bound to draw one of the eight flowers.
        int withFlowers = 0;
        for (long seed = 1; seed <= 10; seed++) {
            RiichiGame game = new RiichiGame(GameRules.taiwanese(), NAMES, seed, null);
            game.startHand();
            for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
                if (!game.seat(seat).flowers().isEmpty()) {
                    withFlowers++;
                }
                // Whatever was set aside, the hand is still the right size.
                int expected = seat == game.dealer() ? 17 : 16;
                assertEquals(expected, game.seat(seat).hand().allConcealed().size());
            }
        }
        assertTrue(withFlowers > 0, "flowers should turn up and be replaced");
    }

    @Test
    void riichiIsNotOnOffer() {
        RiichiGame game = new RiichiGame(GameRules.taiwanese(), NAMES, 5L, null);
        game.startHand();
        for (Action action : game.options(game.currentSeat())) {
            assertTrue(!(action instanceof Action.Discard discard) || !discard.riichi(),
                    "a taiwanese hand has no riichi");
            assertTrue(!(action instanceof Action.NineTerminals),
                    "nine terminals is a japanese abort");
        }
    }

    /**
     * A taiwanese wall has no dead wall, so a flower drawn as the very last tile
     * has nothing to be replaced with. That used to throw; the hand should simply
     * end in a draw. Random play is used rather than the bots because it reaches
     * the end of the wall far more often, and it is cheap: the legal moves for a
     * turn are found without a shanten search.
     */
    @Test
    void aFlowerWithNoReplacementLeftEndsTheHand() {
        for (long seed = 1; seed <= 40; seed++) {
            RiichiGame game = new RiichiGame(GameRules.taiwanese(), NAMES, seed, null);
            java.util.Random random = new java.util.Random(seed);
            game.startHand();
            int steps = 0;
            while (game.phase() != RiichiGame.Phase.IDLE
                    && game.phase() != RiichiGame.Phase.FINISHED) {
                assertTrue(++steps < STEP_LIMIT, "the hand did not finish");
                int seat = seatToAct(game);
                List<Action> options = game.options(seat);
                assertTrue(game.act(seat, options.get(random.nextInt(options.size()))));
            }
            assertNotNull(game.lastResult(), "every hand ends with a result");
        }
    }

    @Test
    void botsPlayAWholeGameAndThePointsBalance() {
        for (long seed = 1; seed <= 2; seed++) {
            RiichiGame game = new RiichiGame(shortGame(), NAMES, seed, null);
            int steps = 0;
            while (!game.isGameOver()) {
                assertTrue(++steps < STEP_LIMIT, "the game did not finish");
                if (game.phase() == RiichiGame.Phase.IDLE) {
                    assertPointsConserved(game);
                    game.startHand();
                    continue;
                }
                int seat = seatToAct(game);
                Action action = SimpleBot.decide(game, seat);
                assertNotNull(action);
                assertTrue(game.act(seat, action), "the bot picked an illegal action");
            }
            assertPointsConserved(game);
        }
    }

    @Test
    void aWonHandIsScoredInTai() {
        for (long seed = 1; seed <= 12; seed++) {
            RiichiGame game = new RiichiGame(shortGame(), NAMES, seed, null);
            int steps = 0;
            while (!game.isGameOver() && steps++ < STEP_LIMIT) {
                if (game.phase() == RiichiGame.Phase.IDLE) {
                    if (game.lastResult() instanceof HandResult.Won won) {
                        assertTrue(won.summary().contains("台"), won.summary());
                        assertEquals(0, java.util.Arrays.stream(won.deltas()).sum());
                        return;
                    }
                    game.startHand();
                    continue;
                }
                int seat = seatToAct(game);
                game.act(seat, SimpleBot.decide(game, seat));
            }
        }
        throw new AssertionError("no taiwanese hand was ever won");
    }
}
