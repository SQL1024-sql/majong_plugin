package io.github.sql1024.mahjong.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YakuEvaluatorTest {

    private static boolean has(HandScore score, Yaku yaku) {
        return score.yaku().stream().anyMatch(value -> value.yaku() == yaku);
    }

    private static int hanOf(HandScore score, Yaku yaku) {
        return score.yaku().stream().filter(value -> value.yaku() == yaku)
                .mapToInt(YakuValue::han).sum();
    }

    @Test
    void pinfuTanyaoRon() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("234567m234p45633s")
                .winTile("6s")
                .seatWind(Wind.SOUTH)
                .roundWind(Wind.EAST)
                .build());
        assertNotNull(score);
        assertTrue(has(score, Yaku.PINFU), score.toString());
        assertTrue(has(score, Yaku.TANYAO), score.toString());
        assertEquals(2, score.han());
        assertEquals(30, score.fu());
        assertEquals(480, score.basePoints());
        assertEquals(2000, ScoreCalculator.ronPayment(score.basePoints(), false, 0));
    }

    @Test
    void riichiTsumoPinfuIsTwentyFu() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("234567m234p45633s")
                .winTile("6s")
                .tsumo(true)
                .riichi(true)
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertEquals(20, score.fu());
        assertEquals(4, score.han());
        assertTrue(has(score, Yaku.MENZEN_TSUMO));
        assertTrue(has(score, Yaku.RIICHI));
        ScoreCalculator.TsumoPayment payment = ScoreCalculator.tsumoPayment(score.basePoints(), false, 0);
        assertEquals(2600, payment.fromDealer());
        assertEquals(1300, payment.fromNonDealer());
    }

    @Test
    void sevenPairsIsTwentyFiveFu() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("1122334455m1122p")
                .winTile("2p")
                .riichi(true)
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertEquals(25, score.fu());
        assertEquals(3, score.han());
        assertTrue(has(score, Yaku.CHIITOITSU));
        assertEquals(3200, ScoreCalculator.ronPayment(score.basePoints(), false, 0));
    }

    @Test
    void concealedHonorTripletFu() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("234567m234p99p111z")
                .winTile("2p")
                .seatWind(Wind.SOUTH)
                .roundWind(Wind.EAST)
                .build());
        assertNotNull(score);
        assertEquals(40, score.fu());
        assertEquals(1, score.han());
        assertTrue(has(score, Yaku.YAKUHAI_ROUND));
        assertFalse(has(score, Yaku.YAKUHAI_SEAT));
        assertEquals(1300, ScoreCalculator.ronPayment(score.basePoints(), false, 0));
    }

    @Test
    void doubleWindCountsTwice() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("234567m234p99p111z")
                .winTile("2p")
                .seatWind(Wind.EAST)
                .roundWind(Wind.EAST)
                .build());
        assertNotNull(score);
        assertEquals(2, score.han());
        assertTrue(has(score, Yaku.YAKUHAI_ROUND));
        assertTrue(has(score, Yaku.YAKUHAI_SEAT));
    }

    @Test
    void thirteenOrphansIsYakuman() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("19m19p19s1234567z1z")
                .winTile("1z")
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertTrue(score.isYakuman());
        assertEquals(2, score.yakumanCount(), score.yaku().toString());
        assertEquals(16000, score.basePoints());
    }

    @Test
    void fourConcealedTripletsIsYakuman() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("111222333m44455p")
                .winTile("4p")
                .tsumo(true)
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertTrue(score.isYakuman());
        assertTrue(has(score, Yaku.SUUANKOU));
        assertEquals(8000, score.basePoints());
        assertEquals(32000, ScoreCalculator.ronPayment(score.basePoints(), false, 0));
    }

    @Test
    void ronOnTripletBreaksFourConcealedTriplets() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("111222333m44455p")
                .winTile("4p")
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertFalse(score.isYakuman());
        assertTrue(has(score, Yaku.SANANKOU));
        assertTrue(has(score, Yaku.TOITOI));
    }

    @Test
    void openHandLosesClosedOnlyYaku() {
        Meld chi = Meld.chi(List.of(Tile.parse("2m"), Tile.parse("3m"), Tile.parse("4m")), Tile.parse("2m"), 3);
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("567m234p45633s")
                .melds(List.of(chi))
                .winTile("6s")
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertFalse(has(score, Yaku.PINFU));
        assertTrue(has(score, Yaku.TANYAO));
        assertEquals(30, score.fu(), "副露的平和型算 30 符");
        assertEquals(1, score.han());
    }

    @Test
    void kuitanCanBeDisabled() {
        Meld chi = Meld.chi(List.of(Tile.parse("2m"), Tile.parse("3m"), Tile.parse("4m")), Tile.parse("2m"), 3);
        Rules rules = new Rules(3, false, true, true, false, 25000, 2, true, true, true, true, 20);
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("567m234p45633s")
                .melds(List.of(chi))
                .winTile("6s")
                .rules(rules)
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertFalse(score.hasYaku());
    }

    @Test
    void countsDoraAndRedFives() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed(List.of(
                        Tile.parse("2m"), Tile.parse("3m"), Tile.parse("4m"),
                        Tile.parse("5m"), Tile.parse("6m"), Tile.parse("7m"),
                        Tile.parse("2p"), Tile.parse("3p"), Tile.parse("4p"),
                        Tile.parse("4s"), Tile.red(Tile.parse("5s").id()), Tile.parse("6s"),
                        Tile.parse("3s"), Tile.parse("3s")))
                .winTile("6s")
                .doraIndicators(List.of(Tile.parse("1m")))
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertEquals(1, hanOf(score, Yaku.AKA_DORA));
        assertEquals(1, hanOf(score, Yaku.DORA), "1m 指示牌 -> 2m 是寶牌");
        assertEquals(4, score.han());
    }

    @Test
    void uraDoraOnlyCountsWithRiichi() {
        WinContext.Builder builder = WinContext.builder()
                .concealed("234567m234p45633s")
                .winTile("6s")
                .seatWind(Wind.SOUTH)
                .uraIndicators(List.of(Tile.parse("1m")));
        assertEquals(0, hanOf(YakuEvaluator.evaluate(builder.build()), Yaku.URA_DORA));
        assertEquals(1, hanOf(YakuEvaluator.evaluate(builder.riichi(true).build()), Yaku.URA_DORA));
    }

    @Test
    void sanshokuAndIttsu() {
        HandScore sanshoku = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("234m234p23467899s")
                .winTile("8s")
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(sanshoku);
        assertTrue(has(sanshoku, Yaku.SANSHOKU_DOUJUN), sanshoku.yaku().toString());

        HandScore ittsu = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("123456789m234p55s")
                .winTile("4p")
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(ittsu);
        assertTrue(has(ittsu, Yaku.ITTSU), ittsu.yaku().toString());
    }

    @Test
    void notAWinReturnsNull() {
        assertNull(YakuEvaluator.evaluate(WinContext.builder()
                .concealed("123456789m123p12z")
                .winTile("2z")
                .build()));
    }

    @Test
    void handWithoutYakuCannotWin() {
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("111456m789p34555s")
                .winTile("1m")
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertFalse(score.hasYaku(), score.yaku().toString());
    }

    @Test
    void picksHighestScoringInterpretation() {
        // 111222333444s + 55s：拆成四暗刻單騎比拆成三組 123s 高
        HandScore score = YakuEvaluator.evaluate(WinContext.builder()
                .concealed("11122233344455s")
                .winTile("5s")
                .tsumo(true)
                .seatWind(Wind.SOUTH)
                .build());
        assertNotNull(score);
        assertTrue(score.isYakuman());
        assertTrue(has(score, Yaku.SUUANKOU_TANKI), score.yaku().toString());
        assertEquals(16000, score.basePoints());
    }
}
