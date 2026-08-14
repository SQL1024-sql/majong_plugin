package com.majong.riichi.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HandEvaluatorTest {

    private static Set<Yaku> yakuOf(HandValue value) {
        return value.yaku().stream().map(ScoredYaku::yaku).collect(Collectors.toSet());
    }

    private static Tile tile(String notation) {
        return Tile.parse(notation);
    }

    @Test
    void pinfuTsumoIsTwoHanTwentyFu() {
        Hand hand = Hand.of("789m345p678p34s55s");
        Tile winning = tile("2s");
        hand.draw(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertEquals(Set.of(Yaku.PINFU, Yaku.MENZEN_TSUMO), yakuOf(value));
        assertEquals(2, value.han());
        assertEquals(20, value.fu());
        assertEquals(400, value.payment().fromNonDealer());
        assertEquals(700, value.payment().fromDealer());
        assertEquals(1500, value.payment().total());
    }

    @Test
    void sevenPairsWithRiichiIsThreeHanTwentyFiveFu() {
        Hand hand = Hand.of("1133m5577p99s11z3z");
        Tile winning = tile("3z");
        hand.add(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .riichi(true)
                .build());

        assertNotNull(value);
        assertEquals(Set.of(Yaku.CHIITOITSU, Yaku.RIICHI), yakuOf(value));
        assertEquals(3, value.han());
        assertEquals(25, value.fu());
        assertEquals(3200, value.payment().fromDiscarder());
    }

    @Test
    void openDragonTripletIsOneHanThirtyFu() {
        Hand hand = Hand.of("234m56799p789s");
        hand.addMeld(Meld.pon(List.of(Tile.of(Tiles.RED_DRAGON), Tile.of(Tiles.RED_DRAGON),
                Tile.of(Tiles.RED_DRAGON)), Tile.of(Tiles.RED_DRAGON), 1));
        Tile winning = tile("9s");

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertEquals(Set.of(Yaku.YAKUHAI_CHUN), yakuOf(value));
        assertEquals(1, value.han());
        assertEquals(30, value.fu());
        assertEquals(1000, value.payment().total());
    }

    @Test
    void threeColourStraightScoresClosed() {
        Hand hand = Hand.of("234567m234p234s55z");
        Tile winning = tile("4m");

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertEquals(Set.of(Yaku.SANSHOKU_DOUJUN), yakuOf(value));
        assertEquals(2, value.han());
        assertEquals(40, value.fu());
        assertEquals(2600, value.payment().fromDiscarder());
    }

    @Test
    void thirteenOrphansWithThirteenWaitsIsADoubleYakuman() {
        Hand hand = Hand.of("19m19p19s1234567z");
        Tile winning = tile("1m");
        hand.add(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertTrue(yakuOf(value).contains(Yaku.KOKUSHI_JUUSANMEN));
        assertEquals(16000, value.basePoints());
        assertEquals(64000, value.payment().total());
    }

    @Test
    void thirteenOrphansOnASingleWaitIsOneYakuman() {
        Hand hand = Hand.of("119m19p1s1234567z");
        Tile winning = tile("9s");
        hand.add(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertTrue(yakuOf(value).contains(Yaku.KOKUSHI));
        assertEquals(8000, value.basePoints());
        assertEquals(32000, value.payment().total());
    }

    @Test
    void fourConcealedTripletsIsAYakuman() {
        Hand hand = Hand.of("11m22m333p555s777z");
        Tile winning = tile("1m");
        hand.draw(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertTrue(yakuOf(value).contains(Yaku.SUUANKOU));
        assertEquals(8000, value.basePoints());
        assertEquals(32000, value.payment().total());
    }

    @Test
    void aTripletCompletedByADiscardIsNotConcealed() {
        Hand hand = Hand.of("11m22m333p555s777z");
        Tile winning = tile("1m");
        hand.add(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        // Only three concealed triplets remain, so this is sanankou, not suuankou.
        assertTrue(yakuOf(value).contains(Yaku.SANANKOU));
        assertTrue(yakuOf(value).contains(Yaku.TOITOI));
    }

    @Test
    void doraAndRedFivesAddHan() {
        Hand hand = Hand.of("789m345p678p34s55s");
        Tile winning = tile("2s");
        hand.draw(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.SOUTH)
                // The indicator 4p makes every 5p dora; the hand holds one.
                .doraIndicators(List.of(tile("4p")))
                .build());

        assertNotNull(value);
        assertEquals(1, value.dora());
        assertEquals(3, value.han());
    }

    @Test
    void uraDoraOnlyCountsUnderRiichi() {
        Hand hand = Hand.of("789m345p678p34s55s");
        Tile winning = tile("2s");
        hand.draw(winning);

        WinContext.Builder base = WinContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.SOUTH)
                .uraIndicators(List.of(tile("4p")));

        assertEquals(0, HandEvaluator.evaluate(hand, base.build()).uraDora());
        assertEquals(1, HandEvaluator.evaluate(hand, base.riichi(true).build()).uraDora());
    }

    @Test
    void aHandWithoutYakuCannotBeDeclared() {
        // Four runs and a pair, but open, so nothing scores.
        Hand hand = Hand.of("234m567m234p99s");
        hand.addMeld(Meld.chi(List.of(tile("4s"), tile("5s"), tile("6s")), tile("4s"), 3));
        Tile winning = tile("7m");

        assertNull(HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build()));
    }

    @Test
    void picksTheHigherScoringReading() {
        // 111222333m can be read as three triplets or three runs; the triplet
        // reading is worth more here.
        Hand hand = Hand.of("111222333m99p22s");
        Tile winning = tile("2s");
        hand.add(winning);

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build());

        assertNotNull(value);
        assertTrue(yakuOf(value).contains(Yaku.SANANKOU));
    }

    @Test
    void dealerRonPaysMore() {
        Hand hand = Hand.of("234567m234p55z");
        hand.addMeld(Meld.pon(List.of(tile("6z"), tile("6z"), tile("6z")), tile("6z"), 2));
        Tile winning = tile("4m");

        HandValue value = HandEvaluator.evaluate(hand, WinContext.builder(winning)
                .seatWind(Tiles.EAST)
                .build());

        assertNotNull(value);
        assertTrue(value.payment().fromDiscarder() > 0);
        assertTrue(yakuOf(value).contains(Yaku.YAKUHAI_HATSU));
    }
}
