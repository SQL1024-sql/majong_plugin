package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.ai.SimpleBot;
import io.github.sql1024.mahjong.core.Rules;
import io.github.sql1024.mahjong.core.Tile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 讓四個電腦玩家把整場打完，確認流程不會卡住或算錯點數。 */
class GameSimulationTest {

    private static MahjongTable newTable(long seed, Rules rules) {
        List<MahjongTable.SeatInfo> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            players.add(new MahjongTable.SeatInfo(UUID.randomUUID(), "CPU" + (i + 1), true));
        }
        return new MahjongTable(rules, players, new Random(seed));
    }

    private static RoundStats playGame(long seed, Rules rules) {
        MahjongTable table = newTable(seed, rules);
        RoundStats stats = new RoundStats();
        int[] snapshot = new int[4];
        table.addListener(new TableListener() {
            @Override
            public void onRoundStart(MahjongTable table) {
                for (int i = 0; i < 4; i++) {
                    snapshot[i] = table.seat(i).points();
                }
            }

            @Override
            public void onRoundEnd(MahjongTable table, RoundResult result) {
                stats.rounds++;
                stats.byType.merge(result.type().name(), 1, Integer::sum);
                int[] deltas = result.deltas();
                for (int i = 0; i < 4; i++) {
                    assertEquals(snapshot[i] + deltas[i], table.seat(i).points(),
                            "第 " + stats.rounds + " 局的增減必須符合實際點數變化");
                }
                int total = table.seats().stream().mapToInt(Seat::points).sum()
                        + table.riichiSticks() * 1000;
                assertEquals(4 * table.rules().startingPoints(), total, "每局結束都要點數守恆");
            }
        });

        table.startRound();
        int guard = 0;
        while (table.phase() != MahjongTable.Phase.GAME_ENDED) {
            if (table.phase() == MahjongTable.Phase.ROUND_ENDED) {
                table.startRound();
            } else if (!SimpleBot.actOnce(table)) {
                throw new IllegalStateException("Nobody could act, phase=" + table.phase());
            }
            if (++guard > 200_000) {
                throw new IllegalStateException("Game did not finish");
            }
        }
        stats.total = table.seats().stream().mapToInt(Seat::points).sum();
        stats.sticks = table.riichiSticks();
        return stats;
    }

    private static final class RoundStats {
        int rounds;
        int total;
        int sticks;
        final java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
    }

    @Test
    void pointsAreConservedAcrossAFullGame() {
        Rules rules = Rules.defaults();
        for (long seed = 1; seed <= 20; seed++) {
            RoundStats stats = playGame(seed, rules);
            assertEquals(4 * rules.startingPoints(), stats.total + stats.sticks * 1000,
                    "全場點數 + 場上立直棒必須守恆 (seed " + seed + ")");
            assertTrue(stats.rounds >= 4, "半莊至少要打 4 局 (seed " + seed + ")");
        }
    }

    @Test
    void eastOnlyGameIsShorter() {
        Rules rules = Rules.defaults().withTargetRounds(1);
        RoundStats stats = playGame(7, rules);
        assertTrue(stats.rounds >= 4, "東風戰至少 4 局");
        assertEquals(4 * rules.startingPoints(), stats.total + stats.sticks * 1000);
    }

    @Test
    void allMajorFeaturesOccurAcrossManyGames() {
        int riichi = 0;
        int calls = 0;
        int wins = 0;
        int draws = 0;
        for (long seed = 100; seed < 110; seed++) {
            MahjongTable table = newTable(seed, Rules.defaults());
            int[] counters = new int[4];
            table.addListener(new TableListener() {
                @Override
                public void onRiichi(MahjongTable table, Seat seat) {
                    counters[0]++;
                }

                @Override
                public void onCall(MahjongTable table, Seat seat, io.github.sql1024.mahjong.core.Meld meld) {
                    counters[1]++;
                }

                @Override
                public void onRoundEnd(MahjongTable table, RoundResult result) {
                    if (result.isDraw()) {
                        counters[3]++;
                    } else {
                        counters[2]++;
                    }
                }
            });
            table.startRound();
            int guard = 0;
            while (table.phase() != MahjongTable.Phase.GAME_ENDED) {
                if (table.phase() == MahjongTable.Phase.ROUND_ENDED) {
                    table.startRound();
                } else if (!SimpleBot.actOnce(table)) {
                    throw new IllegalStateException("Nobody could act");
                }
                if (++guard > 200_000) {
                    throw new IllegalStateException("Game did not finish");
                }
            }
            riichi += counters[0];
            calls += counters[1];
            wins += counters[2];
            draws += counters[3];
        }
        assertTrue(riichi > 0, "應該至少出現一次立直");
        assertTrue(calls > 0, "應該至少出現一次鳴牌");
        assertTrue(wins > 0, "應該至少出現一次和牌");
        assertTrue(draws > 0, "應該至少出現一次流局");
    }

    @Test
    void wallIsDealtCorrectly() {
        MahjongTable table = newTable(42, Rules.defaults());
        table.startRound();
        int handTiles = 0;
        for (Seat seat : table.seats()) {
            handTiles += seat.hand().size();
        }
        // 配牌 13 張 * 4 家，莊家已摸第一張
        assertEquals(52, handTiles);
        assertEquals(69, table.wall().remaining());
        assertEquals(1, table.wall().doraIndicators().size());

        List<Tile> all = table.wall().allTiles();
        assertEquals(136, all.size());
        int[] counts = Tile.toCounts(all);
        for (int id = 0; id < Tile.KINDS; id++) {
            assertEquals(4, counts[id], "每種牌都必須是 4 張");
        }
    }
}
