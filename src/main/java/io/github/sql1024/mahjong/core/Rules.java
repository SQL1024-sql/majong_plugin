package io.github.sql1024.mahjong.core;

/**
 * 規則設定。
 *
 * @param akaCount         赤寶牌張數 (0..4)
 * @param kuitan           是否允許食斷 (副露後的斷么九)
 * @param kazoeYakuman     累計 13 飜是否視為役滿
 * @param doubleYakuman    是否採用雙倍役滿
 * @param kiriageMangan    4 飜 30 符 / 3 飜 60 符是否切上滿貫
 * @param startingPoints   起始點數
 * @param targetRounds     打幾圈 (1 = 東風戰, 2 = 半莊, 4 = 一莊)
 * @param abortiveDraws    是否啟用途中流局 (九種九牌、四風連打、四家立直、四槓散了)
 * @param multipleRon      是否允許多家和 (雙responsible/三家和則流局)
 * @param tenpaiRenchan    莊家聽牌是否連莊
 * @param endOnBankrupt    有人被擊飛 (點數 < 0) 是否結束對局
 * @param turnSeconds      每個決策的思考秒數
 */
public record Rules(
        int akaCount,
        boolean kuitan,
        boolean kazoeYakuman,
        boolean doubleYakuman,
        boolean kiriageMangan,
        int startingPoints,
        int targetRounds,
        boolean abortiveDraws,
        boolean multipleRon,
        boolean tenpaiRenchan,
        boolean endOnBankrupt,
        int turnSeconds) {

    public static Rules defaults() {
        return new Rules(3, true, true, true, false, 25000, 2, true, true, true, true, 20);
    }

    public Rules withTargetRounds(int rounds) {
        return new Rules(akaCount, kuitan, kazoeYakuman, doubleYakuman, kiriageMangan, startingPoints,
                rounds, abortiveDraws, multipleRon, tenpaiRenchan, endOnBankrupt, turnSeconds);
    }
}
