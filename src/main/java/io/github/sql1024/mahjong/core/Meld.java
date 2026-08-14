package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一組副露。
 *
 * @param type       種類
 * @param tiles      組成的牌 (吃/碰為 3 張、槓為 4 張)
 * @param calledTile 鳴到的那張牌，暗槓為 {@code null}
 * @param fromSeat   被鳴的玩家座位，暗槓為 -1
 */
public record Meld(MeldType type, List<Tile> tiles, Tile calledTile, int fromSeat) {

    public Meld {
        tiles = List.copyOf(tiles);
        if (type.isKan()) {
            if (tiles.size() != 4) {
                throw new IllegalArgumentException("Kan needs 4 tiles");
            }
        } else if (tiles.size() != 3) {
            throw new IllegalArgumentException(type + " needs 3 tiles");
        }
    }

    public static Meld chi(List<Tile> tiles, Tile called, int fromSeat) {
        List<Tile> sorted = new ArrayList<>(tiles);
        Collections.sort(sorted);
        return new Meld(MeldType.CHI, sorted, called, fromSeat);
    }

    public static Meld pon(List<Tile> tiles, Tile called, int fromSeat) {
        return new Meld(MeldType.PON, tiles, called, fromSeat);
    }

    public static Meld ankan(List<Tile> tiles) {
        return new Meld(MeldType.ANKAN, tiles, null, -1);
    }

    public static Meld minkan(List<Tile> tiles, Tile called, int fromSeat) {
        return new Meld(MeldType.MINKAN, tiles, called, fromSeat);
    }

    /** 由碰升級為加槓。 */
    public Meld toKakan(Tile added) {
        if (type != MeldType.PON) {
            throw new IllegalStateException("Only a pon can be upgraded");
        }
        List<Tile> all = new ArrayList<>(tiles);
        all.add(added);
        return new Meld(MeldType.KAKAN, all, calledTile, fromSeat);
    }

    /** 這組牌的「代表牌 id」：順子為最小的那張，刻子/槓為該牌。 */
    public int baseTileId() {
        int min = Integer.MAX_VALUE;
        for (Tile tile : tiles) {
            min = Math.min(min, tile.id());
        }
        return min;
    }

    public boolean isKan() {
        return type.isKan();
    }

    public boolean isSequence() {
        return type == MeldType.CHI;
    }

    /** 刻子或槓 (含順子以外的所有型態)。 */
    public boolean isTripletLike() {
        return type != MeldType.CHI;
    }

    public boolean isConcealed() {
        return type.isConcealed();
    }

    /** 轉換為計分用的面子。 */
    public Group toGroup() {
        if (isSequence()) {
            return Group.sequence(baseTileId(), false);
        }
        return Group.triplet(baseTileId(), type == MeldType.ANKAN, type.isKan());
    }

    public int redCount() {
        int count = 0;
        for (Tile tile : tiles) {
            if (tile.isRed()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(type.displayName()).append('[');
        for (Tile tile : tiles) {
            sb.append(tile.shortName());
        }
        return sb.append(']').toString();
    }
}
