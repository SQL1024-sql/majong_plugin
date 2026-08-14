package io.github.sql1024.mahjong.core;

import java.util.List;

/**
 * 一手和牌的計算結果。
 *
 * @param yaku       成立的役
 * @param han        總飜數 (役滿時為 0)
 * @param fu         符數
 * @param yakumanCount 役滿倍數 (0 表示不是役滿)
 * @param basePoints 基本點 (親 6 倍 / 子 4 倍的那個「基本點」)
 * @param limitName  滿貫以上的名稱，否則為空字串
 */
public record HandScore(List<YakuValue> yaku, int han, int fu, int yakumanCount, int basePoints, String limitName) {

    public HandScore {
        yaku = List.copyOf(yaku);
    }

    public boolean isYakuman() {
        return yakumanCount > 0;
    }

    /** 是否有役 (寶牌不算)。 */
    public boolean hasYaku() {
        if (isYakuman()) {
            return true;
        }
        for (YakuValue value : yaku) {
            if (!value.yaku().isDora()) {
                return true;
            }
        }
        return false;
    }

    public String describeHan() {
        if (isYakuman()) {
            return limitName;
        }
        return han + "飜 " + fu + "符" + (limitName.isEmpty() ? "" : " " + limitName);
    }
}
