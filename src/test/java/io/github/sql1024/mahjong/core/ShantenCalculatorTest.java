package io.github.sql1024.mahjong.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShantenCalculatorTest {

    private static int[] counts(String notation) {
        return Tile.toCounts(Tile.parseHand(notation));
    }

    @Test
    void completeHandIsMinusOne() {
        assertEquals(-1, ShantenCalculator.shanten(counts("123456789m123p11z"), 0));
    }

    @Test
    void tenpaiIsZero() {
        assertEquals(0, ShantenCalculator.shanten(counts("123456789m123p1z"), 0));
        assertEquals(0, ShantenCalculator.shanten(counts("123456789m12p11z"), 0));
    }

    @Test
    void oneShanten() {
        assertEquals(1, ShantenCalculator.shanten(counts("123456789m12p13z"), 0));
    }

    @Test
    void sevenPairsShanten() {
        assertEquals(0, ShantenCalculator.shanten(counts("1122334455m11p2p"), 0));
        assertEquals(-1, ShantenCalculator.sevenPairsShanten(counts("1122334455m1122p")));
    }

    @Test
    void thirteenOrphansShanten() {
        assertEquals(0, ShantenCalculator.shanten(counts("19m19p19s1234567z"), 0));
        assertEquals(1, ShantenCalculator.shanten(counts("19m19p19s123456z2m"), 0));
        assertEquals(-1, ShantenCalculator.thirteenOrphansShanten(counts("19m19p19s1234567z1z")));
    }

    @Test
    void openHandShanten() {
        assertEquals(0, ShantenCalculator.shanten(counts("123456789m1p"), 1));
    }

    @Test
    void listsWaits() {
        List<Integer> waits = ShantenCalculator.waits(counts("123456789m12p11z"), List.of());
        assertEquals(List.of(Tile.parse("3p").id()), waits);

        List<Integer> shanpon = ShantenCalculator.waits(counts("123456789m11p11z"), List.of());
        assertEquals(2, shanpon.size());
        assertTrue(shanpon.contains(Tile.parse("1p").id()));
        assertTrue(shanpon.contains(Tile.parse("1z").id()));
    }

    @Test
    void nineBlockHandWithoutPairIsOneShanten() {
        // 3 面子 + 2 兩面搭子，沒有雀頭
        assertEquals(1, ShantenCalculator.shanten(counts("123456789m12p45s"), 0));
    }
}
