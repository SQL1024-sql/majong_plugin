package com.majong.riichi.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TilesTest {

    @Test
    void parsesNotation() {
        assertEquals(0, Tiles.parse("1m"));
        assertEquals(8, Tiles.parse("9m"));
        assertEquals(9, Tiles.parse("1p"));
        assertEquals(18, Tiles.parse("1s"));
        assertEquals(Tiles.EAST, Tiles.parse("1z"));
        assertEquals(Tiles.RED_DRAGON, Tiles.parse("7z"));
        assertEquals(Tiles.kindOf(Suit.PIN, 5), Tiles.parse("0p"));
    }

    @Test
    void parsesCompactHands() {
        int[] counts = Tiles.parseCounts("123m456p789s11122z");
        assertEquals(14, Tiles.totalCount(counts));
        assertEquals(3, counts[Tiles.EAST]);
        assertEquals(2, counts[Tiles.SOUTH]);
        assertEquals(1, counts[Tiles.kindOf(Suit.MAN, 1)]);
    }

    @Test
    void dorafollowsTheIndicator() {
        assertEquals(Tiles.parse("2m"), Tiles.doraFromIndicator(Tiles.parse("1m")));
        assertEquals(Tiles.parse("1m"), Tiles.doraFromIndicator(Tiles.parse("9m")));
        assertEquals(Tiles.SOUTH, Tiles.doraFromIndicator(Tiles.EAST));
        assertEquals(Tiles.EAST, Tiles.doraFromIndicator(Tiles.NORTH));
        assertEquals(Tiles.GREEN, Tiles.doraFromIndicator(Tiles.WHITE));
        assertEquals(Tiles.WHITE, Tiles.doraFromIndicator(Tiles.RED_DRAGON));
    }

    @Test
    void classifiesTiles() {
        assertTrue(Tiles.isTerminalOrHonor(Tiles.parse("1m")));
        assertTrue(Tiles.isTerminalOrHonor(Tiles.EAST));
        assertFalse(Tiles.isTerminalOrHonor(Tiles.parse("5p")));
        assertTrue(Tiles.canStartRun(Tiles.parse("7s")));
        assertFalse(Tiles.canStartRun(Tiles.parse("8s")));
        assertFalse(Tiles.canStartRun(Tiles.EAST));
    }

    @Test
    void redFivesKeepTheirOwnNotation() {
        Tile red = Tile.red(Suit.SOU);
        assertTrue(red.red());
        assertEquals("0s", red.notation());
        assertEquals(Tiles.kindOf(Suit.SOU, 5), red.kind());
    }
}
