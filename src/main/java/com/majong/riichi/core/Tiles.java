package com.majong.riichi.core;

/**
 * Helpers around the 34 distinct tile kinds.
 *
 * <p>Kind indices are laid out as: {@code 0-8} man 1-9, {@code 9-17} pin 1-9,
 * {@code 18-26} sou 1-9, {@code 27-30} the winds east/south/west/north and
 * {@code 31-33} the dragons white/green/red.
 */
public final class Tiles {

    public static final int KINDS = 34;

    public static final int MAN_1 = 0;
    public static final int PIN_1 = 9;
    public static final int SOU_1 = 18;
    public static final int EAST = 27;
    public static final int SOUTH = 28;
    public static final int WEST = 29;
    public static final int NORTH = 30;
    public static final int WHITE = 31;
    public static final int GREEN = 32;
    public static final int RED_DRAGON = 33;

    private static final String[] HONOR_NAMES = {"東", "南", "西", "北", "白", "發", "中"};

    private Tiles() {
    }

    public static Suit suitOf(int kind) {
        checkKind(kind);
        if (kind < PIN_1) {
            return Suit.MAN;
        }
        if (kind < SOU_1) {
            return Suit.PIN;
        }
        if (kind < EAST) {
            return Suit.SOU;
        }
        return Suit.HONOR;
    }

    /** Returns 1-9 for numbered tiles, or 1-7 for honors (east..red dragon). */
    public static int rankOf(int kind) {
        checkKind(kind);
        if (kind >= EAST) {
            return kind - EAST + 1;
        }
        return kind % 9 + 1;
    }

    public static int kindOf(Suit suit, int rank) {
        if (suit == Suit.HONOR) {
            if (rank < 1 || rank > 7) {
                throw new IllegalArgumentException("honor rank out of range: " + rank);
            }
            return EAST + rank - 1;
        }
        if (rank < 1 || rank > 9) {
            throw new IllegalArgumentException("number rank out of range: " + rank);
        }
        return switch (suit) {
            case MAN -> MAN_1 + rank - 1;
            case PIN -> PIN_1 + rank - 1;
            case SOU -> SOU_1 + rank - 1;
            default -> throw new IllegalStateException();
        };
    }

    public static boolean isHonor(int kind) {
        return kind >= EAST;
    }

    public static boolean isWind(int kind) {
        return kind >= EAST && kind <= NORTH;
    }

    public static boolean isDragon(int kind) {
        return kind >= WHITE;
    }

    /** True for terminals (1 and 9) and every honor tile. */
    public static boolean isTerminalOrHonor(int kind) {
        if (isHonor(kind)) {
            return true;
        }
        int rank = rankOf(kind);
        return rank == 1 || rank == 9;
    }

    public static boolean isTerminal(int kind) {
        return !isHonor(kind) && (rankOf(kind) == 1 || rankOf(kind) == 9);
    }

    public static boolean isSimple(int kind) {
        return !isTerminalOrHonor(kind);
    }

    /** True when a run may start on this tile (numbered, rank 1-7). */
    public static boolean canStartRun(int kind) {
        return !isHonor(kind) && rankOf(kind) <= 7;
    }

    /** The tile a dora indicator points at. */
    public static int doraFromIndicator(int indicator) {
        checkKind(indicator);
        if (indicator <= NORTH && indicator >= EAST) {
            return indicator == NORTH ? EAST : indicator + 1;
        }
        if (indicator >= WHITE) {
            return indicator == RED_DRAGON ? WHITE : indicator + 1;
        }
        int rank = rankOf(indicator);
        return rank == 9 ? indicator - 8 : indicator + 1;
    }

    /** Parses notation such as {@code 3m}, {@code 7z} or {@code 0p} (red five). */
    public static int parse(String text) {
        String trimmed = text.trim();
        if (trimmed.length() != 2) {
            throw new IllegalArgumentException("bad tile notation: " + text);
        }
        char rankChar = trimmed.charAt(0);
        if (rankChar < '0' || rankChar > '9') {
            throw new IllegalArgumentException("bad tile notation: " + text);
        }
        Suit suit = Suit.fromCode(Character.toLowerCase(trimmed.charAt(1)));
        int rank = rankChar - '0';
        if (rank == 0) {
            if (suit == Suit.HONOR) {
                throw new IllegalArgumentException("honors have no red five: " + text);
            }
            rank = 5;
        }
        return kindOf(suit, rank);
    }

    /** Parses a compact hand such as {@code 123m456p789s11122z}. */
    public static int[] parseCounts(String text) {
        int[] counts = new int[KINDS];
        for (int kind : parseAll(text)) {
            counts[kind]++;
        }
        return counts;
    }

    /** Parses a compact hand into its individual tile kinds, in written order. */
    public static int[] parseAll(String text) {
        StringBuilder pending = new StringBuilder();
        java.util.List<Integer> kinds = new java.util.ArrayList<>();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c >= '0' && c <= '9') {
                pending.append(c);
                continue;
            }
            Suit suit = Suit.fromCode(Character.toLowerCase(c));
            if (pending.isEmpty()) {
                throw new IllegalArgumentException("suit '" + c + "' without ranks in: " + text);
            }
            for (int i = 0; i < pending.length(); i++) {
                int rank = pending.charAt(i) - '0';
                kinds.add(kindOf(suit, rank == 0 ? 5 : rank));
            }
            pending.setLength(0);
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("trailing ranks without a suit in: " + text);
        }
        int[] result = new int[kinds.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = kinds.get(i);
        }
        return result;
    }

    /** Short latin notation, e.g. {@code 3m}. */
    public static String notation(int kind) {
        checkKind(kind);
        return rankOf(kind) + String.valueOf(suitOf(kind).code());
    }

    /** Display name using the usual japanese characters for honors. */
    public static String display(int kind) {
        checkKind(kind);
        if (isHonor(kind)) {
            return HONOR_NAMES[kind - EAST];
        }
        return notation(kind);
    }

    public static int totalCount(int[] counts) {
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        return total;
    }

    private static void checkKind(int kind) {
        if (kind < 0 || kind >= KINDS) {
            throw new IllegalArgumentException("tile kind out of range: " + kind);
        }
    }
}
