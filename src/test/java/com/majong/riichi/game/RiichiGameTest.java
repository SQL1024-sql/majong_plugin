package com.majong.riichi.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.majong.riichi.core.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RiichiGameTest {

    private static final List<String> NAMES = List.of("east", "south", "west", "north");
    private static final int STEP_LIMIT = 100_000;

    /** Points never appear or vanish: sticks on the table make up the difference. */
    private static void assertPointsConserved(RiichiGame game) {
        int total = game.riichiSticks() * 1000;
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            total += game.seat(seat).score();
        }
        assertEquals(100_000, total);
    }

    /** Plays a whole game with players that pick a legal action at random. */
    private static RiichiGame playOut(long seed) {
        Random random = new Random(seed);
        RiichiGame game = new RiichiGame(GameRules.hanchan(), NAMES, seed, null);
        int steps = 0;
        while (!game.isGameOver()) {
            assertTrue(++steps < STEP_LIMIT, "the game did not finish");
            switch (game.phase()) {
                case IDLE -> {
                    assertPointsConserved(game);
                    game.startHand();
                }
                case ACT -> {
                    int seat = game.currentSeat();
                    List<Action> options = game.options(seat);
                    assertTrue(!options.isEmpty(), "a player on turn always has something to do");
                    assertTrue(game.act(seat, choose(options, random)));
                }
                case CALL -> {
                    int seat = nextResponder(game);
                    assertTrue(game.act(seat, choose(game.options(seat), random)));
                }
                case FINISHED -> {
                }
            }
        }
        assertPointsConserved(game);
        return game;
    }

    private static int nextResponder(RiichiGame game) {
        for (int seat : game.pendingSeats()) {
            if (!game.options(seat).isEmpty()) {
                return seat;
            }
        }
        throw new IllegalStateException("the table is waiting on nobody");
    }

    /** Always finish a hand when possible, otherwise pick at random. */
    private static Action choose(List<Action> options, Random random) {
        for (Action action : options) {
            if (action instanceof Action.Tsumo || action instanceof Action.Ron) {
                return action;
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    @Test
    void dealsThirteenTilesToEverybody() {
        RiichiGame game = new RiichiGame(GameRules.hanchan(), NAMES, 1L, null);
        game.startHand();

        assertEquals(RiichiGame.Phase.ACT, game.phase());
        assertEquals(0, game.currentSeat());
        assertEquals(14, game.seat(0).hand().allConcealed().size());
        for (int seat = 1; seat < RiichiGame.SEATS; seat++) {
            assertEquals(13, game.seat(seat).hand().allConcealed().size());
        }
        // Seventy tiles are drawable in a hand; the dealer has taken the first.
        assertEquals(69, game.wallRemaining());
        assertEquals(1, game.doraIndicators().size());
    }

    @Test
    void seatWindsFollowTheDealer() {
        RiichiGame game = new RiichiGame(GameRules.hanchan(), NAMES, 1L, null);
        assertEquals(Tiles.EAST, game.seatWind(0));
        assertEquals(Tiles.SOUTH, game.seatWind(1));
        assertEquals(Tiles.WEST, game.seatWind(2));
        assertEquals(Tiles.NORTH, game.seatWind(3));
    }

    @Test
    void aFullGameFinishesAndKeepsThePointsBalanced() {
        for (long seed = 1; seed <= 20; seed++) {
            RiichiGame game = playOut(seed);
            assertTrue(game.isGameOver());
            assertNotNull(game.lastResult());
            assertEquals(RiichiGame.SEATS, game.standings().size());
        }
    }

    @Test
    void everyHandEndsInAResult() {
        Random random = new Random(7);
        RiichiGame game = new RiichiGame(GameRules.tonpuusen(), NAMES, 7L, null);
        List<HandResult> results = new ArrayList<>();
        int steps = 0;
        while (!game.isGameOver()) {
            assertTrue(++steps < STEP_LIMIT);
            switch (game.phase()) {
                case IDLE -> {
                    if (game.lastResult() != null) {
                        results.add(game.lastResult());
                    }
                    game.startHand();
                }
                case ACT -> game.act(game.currentSeat(), choose(game.options(game.currentSeat()), random));
                case CALL -> {
                    int seat = nextResponder(game);
                    game.act(seat, choose(game.options(seat), random));
                }
                case FINISHED -> {
                }
            }
        }
        results.add(game.lastResult());
        assertTrue(results.size() >= 4, "an east game plays at least four hands");
        for (HandResult result : results) {
            assertEquals(RiichiGame.SEATS, result.deltas().length);
        }
    }

    @Test
    void riichiCostsAThousandPointStick() {
        // Players discarding at random reach a ready hand only now and then, so
        // walk through games until one of them declares.
        for (long seed = 1; seed <= 40; seed++) {
            Random random = new Random(seed);
            RiichiGame game = new RiichiGame(GameRules.hanchan(), NAMES, seed, null);
            int steps = 0;
            while (!game.isGameOver() && steps++ < STEP_LIMIT) {
                if (game.phase() == RiichiGame.Phase.IDLE) {
                    game.startHand();
                    continue;
                }
                if (game.phase() == RiichiGame.Phase.CALL) {
                    int seat = nextResponder(game);
                    game.act(seat, choose(game.options(seat), random));
                    continue;
                }
                int seat = game.currentSeat();
                List<Action> options = game.options(seat);
                Action riichi = options.stream()
                        .filter(action -> action instanceof Action.Discard discard && discard.riichi())
                        .findFirst()
                        .orElse(null);
                if (riichi != null) {
                    int before = game.seat(seat).score();
                    int sticksBefore = game.riichiSticks();
                    assertTrue(game.act(seat, riichi));
                    assertTrue(game.seat(seat).riichi());
                    assertEquals(before - 1000, game.seat(seat).score());
                    assertEquals(sticksBefore + 1, game.riichiSticks());
                    assertPointsConserved(game);
                    return;
                }
                game.act(seat, choose(options, random));
            }
        }
        throw new AssertionError("no riichi was ever offered across forty games");
    }
}
