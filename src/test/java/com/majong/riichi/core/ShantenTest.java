package com.majong.riichi.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShantenTest {

    @Test
    void aFinishedHandIsMinusOne() {
        assertEquals(-1, Shanten.calculate(Tiles.parseCounts("123456789m123p55s"), 0));
    }

    @Test
    void aReadyHandIsZero() {
        assertEquals(0, Shanten.calculate(Tiles.parseCounts("123456789m12p55s"), 0));
        assertEquals(0, Shanten.calculate(Tiles.parseCounts("123456789m11p55s"), 0));
    }

    @Test
    void countsStepsForAnUnfinishedHand() {
        assertEquals(1, Shanten.calculate(Tiles.parseCounts("123456m789m12p57s"), 0));
        assertTrue(Shanten.calculate(Tiles.parseCounts("159m159p159s1234z"), 0) > 2);
    }

    @Test
    void sevenPairsIsCountedSeparately() {
        assertEquals(0, Shanten.sevenPairs(Tiles.parseCounts("1133m5577p99s11z3z")));
        assertEquals(-1, Shanten.sevenPairs(Tiles.parseCounts("1133m5577p99s1133z")));
    }

    @Test
    void thirteenOrphansIsCountedSeparately() {
        assertEquals(0, Shanten.thirteenOrphans(Tiles.parseCounts("19m19p19s1234567z")));
        assertEquals(-1, Shanten.thirteenOrphans(Tiles.parseCounts("119m19p19s1234567z")));
    }

    @Test
    void agreesWithTheWinCheckerOnReadiness() {
        String[] hands = {
            "123456789m12p55s", "123456789m11p55s", "1133m5577p99s11z3z",
            "19m19p19s1234567z", "123456m789m12p57s", "159m159p159s1234z"
        };
        for (String notation : hands) {
            int[] counts = Tiles.parseCounts(notation);
            boolean tenpai = WinChecker.isTenpai(counts, 0);
            assertEquals(tenpai, Shanten.calculate(counts, 0) == 0, notation);
        }
    }

    @Test
    void improvingTilesLeadTowardsReady() {
        int[] counts = Tiles.parseCounts("123456m789m12p57s");
        var useful = Shanten.improvingTiles(counts, 0);
        assertTrue(useful.contains(Tiles.parse("6s")), "6s bridges the 5s7s gap");
        assertTrue(useful.contains(Tiles.parse("3p")));
    }
}
