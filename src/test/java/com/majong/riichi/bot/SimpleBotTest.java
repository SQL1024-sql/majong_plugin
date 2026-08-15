package com.majong.riichi.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.majong.riichi.game.Action;
import com.majong.riichi.game.GameRules;
import com.majong.riichi.game.HandResult;
import com.majong.riichi.game.RiichiGame;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleBotTest {

    private static final List<String> NAMES = List.of("bot0", "bot1", "bot2", "bot3");

    @Test
    void fourBotsPlayAGameToTheEnd() {
        for (long seed = 1; seed <= 3; seed++) {
            RiichiGame game = new RiichiGame(GameRules.hanchan(), NAMES, seed, null);
            int steps = 0;
            while (!game.isGameOver()) {
                assertTrue(++steps < 100_000, "the bots did not finish the game");
                if (game.phase() == RiichiGame.Phase.IDLE) {
                    game.startHand();
                    continue;
                }
                int seat = seatToAct(game);
                Action action = SimpleBot.decide(game, seat);
                assertNotNull(action, "a bot must always have something to play");
                assertTrue(game.act(seat, action), "the bot picked an illegal action");
            }
            int total = game.riichiSticks() * 1000;
            for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
                total += game.seat(seat).score();
            }
            assertEquals(100_000, total);
        }
    }

    @Test
    void botsActuallyFinishHands() {
        int won = 0;
        for (long seed = 1; seed <= 3; seed++) {
            RiichiGame game = new RiichiGame(GameRules.tonpuusen(), NAMES, seed, null);
            int steps = 0;
            while (!game.isGameOver() && steps++ < 100_000) {
                if (game.phase() == RiichiGame.Phase.IDLE) {
                    if (game.lastResult() instanceof HandResult.Won) {
                        won++;
                    }
                    game.startHand();
                    continue;
                }
                int seat = seatToAct(game);
                game.act(seat, SimpleBot.decide(game, seat));
            }
            if (game.lastResult() instanceof HandResult.Won) {
                won++;
            }
        }
        assertTrue(won > 0, "bots aiming for a ready hand should win one sooner or later");
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
}
