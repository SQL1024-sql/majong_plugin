package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.HandScore;
import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.Tile;

import java.util.List;

/**
 * 一位玩家的和牌內容。
 *
 * @param seat      和牌者座位
 * @param fromSeat  放銃者座位，自摸為 -1
 * @param winTile   和了牌
 * @param concealed 門前手牌 (含和了牌)
 * @param melds     副露
 * @param score     計分結果
 * @param gain      這位玩家實際得到的點數 (含本場與立直棒)
 */
public record WinEntry(int seat, int fromSeat, Tile winTile, List<Tile> concealed, List<Meld> melds,
                       HandScore score, int gain) {

    public WinEntry {
        concealed = List.copyOf(concealed);
        melds = List.copyOf(melds);
    }

    public boolean isTsumo() {
        return fromSeat < 0;
    }
}
