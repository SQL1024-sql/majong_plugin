package com.majong.riichi.core;

/**
 * One building block of a finished hand.
 *
 * @param type      run, triplet, kan or the pair
 * @param kind      the tile it is built on; for a run the lowest tile
 * @param concealed whether it was formed without calling an opponent's tile
 */
public record Group(GroupType type, int kind, boolean concealed) {

    public static Group run(int kind, boolean concealed) {
        return new Group(GroupType.RUN, kind, concealed);
    }

    public static Group triplet(int kind, boolean concealed) {
        return new Group(GroupType.TRIPLET, kind, concealed);
    }

    public static Group kan(int kind, boolean concealed) {
        return new Group(GroupType.KAN, kind, concealed);
    }

    public static Group pair(int kind) {
        return new Group(GroupType.PAIR, kind, true);
    }

    public static Group fromMeld(Meld meld) {
        return switch (meld.type()) {
            case CHI -> run(meld.baseKind(), false);
            case PON -> triplet(meld.baseKind(), false);
            case ANKAN -> kan(meld.baseKind(), true);
            case DAIMINKAN, SHOUMINKAN -> kan(meld.baseKind(), false);
        };
    }

    public boolean isSet() {
        return type != GroupType.PAIR;
    }

    public boolean isTripletLike() {
        return type == GroupType.TRIPLET || type == GroupType.KAN;
    }

    /** Whether the group contains the given tile kind. */
    public boolean contains(int tileKind) {
        if (type == GroupType.RUN) {
            return tileKind >= kind && tileKind <= kind + 2;
        }
        return tileKind == kind;
    }

    /** Every tile kind in the group, a run expanded to its three tiles. */
    public int[] kinds() {
        if (type == GroupType.RUN) {
            return new int[]{kind, kind + 1, kind + 2};
        }
        return new int[]{kind};
    }

    public boolean isTerminalOrHonorGroup() {
        if (type == GroupType.RUN) {
            return Tiles.rankOf(kind) == 1 || Tiles.rankOf(kind) == 7;
        }
        return Tiles.isTerminalOrHonor(kind);
    }

    public boolean hasTerminalOrHonor() {
        for (int tileKind : kinds()) {
            if (Tiles.isTerminalOrHonor(tileKind)) {
                return true;
            }
        }
        return false;
    }
}
