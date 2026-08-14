package io.github.sql1024.mahjong.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandParserTest {

    private static int[] counts(String notation) {
        return Tile.toCounts(Tile.parseHand(notation));
    }

    @Test
    void parsesStandardHand() {
        assertTrue(HandParser.isWinningHand(counts("123456789m123p11z"), List.of()));
    }

    @Test
    void rejectsIncompleteHand() {
        assertFalse(HandParser.isWinningHand(counts("123456789m123p12z"), List.of()));
    }

    @Test
    void parsesSevenPairs() {
        assertTrue(HandParser.isSevenPairs(counts("1122334455m1122p")));
        assertFalse(HandParser.isSevenPairs(counts("1111223344m1122p")));
    }

    @Test
    void parsesThirteenOrphans() {
        assertTrue(HandParser.isThirteenOrphans(counts("19m19p19s1234567z1z")));
        assertFalse(HandParser.isThirteenOrphans(counts("19m19p19s1234567z2m")));
    }

    @Test
    void openHandUsesMelds() {
        Meld pon = Meld.pon(List.of(Tile.parse("1z"), Tile.parse("1z"), Tile.parse("1z")), Tile.parse("1z"), 1);
        assertTrue(HandParser.isWinningHand(counts("123456789m11p"), List.of(pon)));
    }

    @Test
    void doesNotDuplicateDecompositions() {
        // 111222333444m 恰好三種拆法：四暗刻 / 123m×3+444m / 111m+234m×3
        List<HandParse> parses = HandParser.parse(counts("111222333444m99p"), List.of());
        assertEquals(3, parses.size(), parses.toString());
    }

    @Test
    void doraIndicatorWrapsAround() {
        assertEquals(Tile.parse("1m"), Tile.parse("9m").doraFromIndicator());
        assertEquals(Tile.parse("1z"), Tile.parse("4z").doraFromIndicator());
        assertEquals(Tile.parse("5z"), Tile.parse("7z").doraFromIndicator());
        assertEquals(Tile.parse("6p"), Tile.parse("5p").doraFromIndicator());
    }
}
