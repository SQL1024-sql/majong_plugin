package io.github.sql1024.mahjong.core;

/**
 * 和牌拆解後的一組牌 (面子或雀頭)。
 *
 * @param kind     種類
 * @param tileId   代表牌 (順子為最小的那張)
 * @param concealed 是否為暗的 (暗刻/暗槓)
 * @param kan      是否為槓
 */
public record Group(Kind kind, int tileId, boolean concealed, boolean kan) {

    public enum Kind {
        /** 順子 */
        SEQUENCE,
        /** 刻子 (含槓) */
        TRIPLET,
        /** 雀頭 */
        PAIR
    }

    public static Group sequence(int tileId, boolean concealed) {
        return new Group(Kind.SEQUENCE, tileId, concealed, false);
    }

    public static Group triplet(int tileId, boolean concealed, boolean kan) {
        return new Group(Kind.TRIPLET, tileId, concealed, kan);
    }

    public static Group pair(int tileId) {
        return new Group(Kind.PAIR, tileId, true, false);
    }

    public boolean isSequence() {
        return kind == Kind.SEQUENCE;
    }

    public boolean isTriplet() {
        return kind == Kind.TRIPLET;
    }

    public boolean isPair() {
        return kind == Kind.PAIR;
    }

    /** 這組牌是否含有指定的牌。 */
    public boolean contains(int id) {
        if (isSequence()) {
            return id >= tileId && id <= tileId + 2;
        }
        return id == tileId;
    }

    /** 這組牌是否含有么九牌。 */
    public boolean containsTerminalOrHonor() {
        if (isSequence()) {
            return Tile.of(tileId).isTerminal() || Tile.of(tileId + 2).isTerminal();
        }
        return Tile.of(tileId).isTerminalOrHonor();
    }

    /** 這組牌是否全部由么九牌組成。 */
    public boolean allTerminalOrHonor() {
        if (isSequence()) {
            return false;
        }
        return Tile.of(tileId).isTerminalOrHonor();
    }

    /** 這組牌是否全部由老頭牌 (1/9) 組成。 */
    public boolean allTerminal() {
        if (isSequence()) {
            return false;
        }
        return Tile.of(tileId).isTerminal();
    }

    /** 這組牌是否全部由綠色牌組成。 */
    public boolean allGreen() {
        if (isSequence()) {
            return Tile.of(tileId).suit() == Suit.SOU && Tile.of(tileId).number() == 2;
        }
        return Tile.of(tileId).isGreen();
    }

    /** 組成的牌數 (槓算 4 張)。 */
    public int size() {
        if (isPair()) {
            return 2;
        }
        return kan ? 4 : 3;
    }

    public Suit suit() {
        return Tile.of(tileId).suit();
    }

    @Override
    public String toString() {
        return switch (kind) {
            case SEQUENCE -> "順" + Tile.of(tileId).shortName();
            case TRIPLET -> (concealed ? "暗" : "明") + (kan ? "槓" : "刻") + Tile.of(tileId).shortName();
            case PAIR -> "對" + Tile.of(tileId).shortName();
        };
    }
}
