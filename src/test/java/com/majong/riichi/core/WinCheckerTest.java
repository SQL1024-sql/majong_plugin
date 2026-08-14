package com.majong.riichi.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WinCheckerTest {

    @Test
    void recognisesAStandardHand() {
        assertTrue(WinChecker.isWinningHand(Tiles.parseCounts("123456789m123p55s"), 0));
        assertFalse(WinChecker.isWinningHand(Tiles.parseCounts("123456789m123p56s"), 0));
    }

    @Test
    void recognisesAHandWithMelds() {
        // Eleven concealed tiles plus one meld still finishes a hand.
        assertTrue(WinChecker.isWinningHand(Tiles.parseCounts("123m456m789m11p"), 1));
        assertFalse(WinChecker.isWinningHand(Tiles.parseCounts("123m456m789m12p"), 1));
    }

    @Test
    void recognisesSevenPairs() {
        assertTrue(WinChecker.isSevenPairs(Tiles.parseCounts("1133m5577p99s1177z")));
        // Four of a kind is not two pairs.
        assertFalse(WinChecker.isSevenPairs(Tiles.parseCounts("1111m5577p99s1177z")));
    }

    @Test
    void recognisesThirteenOrphans() {
        assertTrue(WinChecker.isThirteenOrphans(Tiles.parseCounts("19m19p19s1234567z1z")));
        assertFalse(WinChecker.isThirteenOrphans(Tiles.parseCounts("19m19p19s1234567z2m")));
    }

    @Test
    void enumeratesAmbiguousShapes() {
        // 111222333m can be read as three triplets or three identical runs.
        var decompositions = WinChecker.standardDecompositions(Tiles.parseCounts("111222333m99p"), 1);
        assertEquals(2, decompositions.size());
    }

    @Test
    void findsWaits() {
        Set<Integer> waits = WinChecker.waits(Tiles.parseCounts("123456789m12p55s"), 0);
        assertEquals(Set.of(Tiles.parse("3p")), waits);

        Set<Integer> shanpon = WinChecker.waits(Tiles.parseCounts("123456789m11p55s"), 0);
        assertEquals(Set.of(Tiles.parse("1p"), Tiles.parse("5s")), shanpon);
    }

    @Test
    void thirteenOrphansWaitsOnEverything() {
        Set<Integer> waits = WinChecker.waits(Tiles.parseCounts("19m19p19s1234567z"), 0);
        assertEquals(13, waits.size());
    }

    @Test
    void tenpaiNeedsOneMoreTile() {
        assertTrue(WinChecker.isTenpai(Tiles.parseCounts("123456789m12p55s"), 0));
        assertFalse(WinChecker.isTenpai(Tiles.parseCounts("123456789m14p55s"), 0));
    }
}
