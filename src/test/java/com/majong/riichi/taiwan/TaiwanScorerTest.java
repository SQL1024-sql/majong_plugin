package com.majong.riichi.taiwan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.majong.riichi.core.Flower;
import com.majong.riichi.core.Hand;
import com.majong.riichi.core.Meld;
import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TaiwanScorerTest {

    private static final TaiwanRules RULES = TaiwanRules.standard();

    private static Set<Tai> patterns(TaiwanValue value) {
        return value.patterns().stream().map(TaiwanValue.ScoredTai::pattern)
                .collect(Collectors.toSet());
    }

    private static Tile tile(String notation) {
        return Tile.parse(notation);
    }

    @Test
    void thirtyBaseAndTenTaiIsTheStake() {
        assertEquals(30, RULES.base());
        assertEquals(10, RULES.perTai());
        assertEquals(30, RULES.payment(0));
        assertEquals(80, RULES.payment(5));
    }

    @Test
    void aPlainHandPaysTheBaseOnly() {
        // Two called runs leave the hand open, so nothing else scores.
        Hand hand = Hand.of("234m567m234p99s");
        hand.addMeld(Meld.chi(List.of(tile("4s"), tile("5s"), tile("6s")), tile("4s"), 3));
        hand.addMeld(Meld.chi(List.of(tile("6p"), tile("7p"), tile("8p")), tile("6p"), 3));
        Tile winning = tile("4m");

        TaiwanValue value = TaiwanScorer.score(hand, TaiwanContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build(), RULES);

        assertNotNull(value);
        assertEquals(0, value.tai());
        assertEquals(30, value.fromDiscarder());
        assertEquals(30, value.total());
    }

    @Test
    void concealedSelfDrawnFlatHandIsFiveTai() {
        Hand hand = Hand.of("12345789m123456p99s");
        Tile winning = tile("6m");
        hand.draw(winning);

        TaiwanValue value = TaiwanScorer.score(hand, TaiwanContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.SOUTH)
                .build(), RULES);

        assertNotNull(value);
        assertEquals(Set.of(Tai.MENQING, Tai.ZIMO, Tai.MENQING_ZIMO, Tai.PINGHU), patterns(value));
        assertEquals(5, value.tai());
        // Thirty base plus five tai at ten each, from each of the other three.
        assertEquals(80, value.perPlayer());
        assertEquals(240, value.total());
    }

    @Test
    void allTripletsSelfDrawnByTheDealer() {
        Hand hand = Hand.of("11m22m999m333p555s777z");
        Tile winning = tile("1m");
        hand.draw(winning);

        TaiwanValue value = TaiwanScorer.score(hand, TaiwanContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.EAST)
                .build(), RULES);

        assertNotNull(value);
        assertTrue(patterns(value).containsAll(Set.of(
                Tai.MENQING, Tai.ZIMO, Tai.MENQING_ZIMO, Tai.PENGPENGHU,
                Tai.WUANKE, Tai.YAKUHAI_ZHONG, Tai.DEALER)));
        assertEquals(17, value.tai());
        assertEquals(200, value.perPlayer());
        assertEquals(600, value.total());
    }

    @Test
    void aTripletFinishedByADiscardIsNotConcealed() {
        Hand hand = Hand.of("11m22m999m333p555s777z");
        Tile winning = tile("1m");
        hand.add(winning);

        TaiwanValue value = TaiwanScorer.score(hand, TaiwanContext.builder(winning)
                .seatWind(Tiles.SOUTH)
                .build(), RULES);

        assertNotNull(value);
        // Four of the five triplets stay concealed, so it is not five concealed.
        assertTrue(patterns(value).contains(Tai.SIANKE));
        assertTrue(patterns(value).contains(Tai.PENGPENGHU));
    }

    @Test
    void onlyFlowersMatchingTheSeatScore() {
        Hand hand = Hand.of("12345789m123456p99s");
        Tile winning = tile("6m");
        hand.draw(winning);

        // South is seat one, so summer and orchid match and spring does not.
        TaiwanValue value = TaiwanScorer.score(hand, TaiwanContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.SOUTH)
                .flowers(List.of(Flower.SPRING, Flower.SUMMER, Flower.ORCHID))
                .build(), RULES);

        assertNotNull(value);
        assertEquals(7, value.tai());
        assertEquals(100, value.perPlayer());
    }

    @Test
    void keepingTheDealerSeatAddsTaiEachTime() {
        Hand hand = Hand.of("12345789m123456p99s");
        Tile winning = tile("6m");
        hand.draw(winning);

        TaiwanValue value = TaiwanScorer.score(hand, TaiwanContext.builder(winning)
                .tsumo(true)
                .seatWind(Tiles.EAST)
                .dealerStreak(2)
                .build(), RULES);

        assertNotNull(value);
        // Five for the hand, one for holding the dealer seat, two for each repeat.
        assertEquals(10, value.tai());
    }

    @Test
    void sixteenTilesAreNeeded() {
        // A japanese thirteen tile hand does not finish a taiwanese one.
        Hand hand = Hand.of("123456789m123p55s");
        assertNull(TaiwanScorer.score(hand,
                TaiwanContext.builder(tile("5s")).seatWind(Tiles.SOUTH).build(), RULES));
    }
}
