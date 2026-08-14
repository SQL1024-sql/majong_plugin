package io.github.sql1024.mahjong.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一張麻將牌。
 *
 * <p>牌以 0..33 的 id 表示：
 * <ul>
 *   <li>0..8   一萬 ~ 九萬</li>
 *   <li>9..17  一筒 ~ 九筒</li>
 *   <li>18..26 一索 ~ 九索</li>
 *   <li>27..33 東 南 西 北 白 發 中</li>
 * </ul>
 * 赤寶牌 (紅五) 與一般五同 id，另以 {@link #isRed()} 區分。
 */
public final class Tile implements Comparable<Tile> {

    public static final int MAN_START = 0;
    public static final int PIN_START = 9;
    public static final int SOU_START = 18;
    public static final int HONOR_START = 27;

    public static final int EAST = 27;
    public static final int SOUTH = 28;
    public static final int WEST = 29;
    public static final int NORTH = 30;
    public static final int HAKU = 31;
    public static final int HATSU = 32;
    public static final int CHUN = 33;

    /** 不同牌種的數量。 */
    public static final int KINDS = 34;

    private static final Tile[] NORMAL = new Tile[KINDS];
    private static final Tile[] RED = new Tile[KINDS];

    private static final String[] NAMES = {
            "一萬", "二萬", "三萬", "四萬", "五萬", "六萬", "七萬", "八萬", "九萬",
            "一筒", "二筒", "三筒", "四筒", "五筒", "六筒", "七筒", "八筒", "九筒",
            "一索", "二索", "三索", "四索", "五索", "六索", "七索", "八索", "九索",
            "東", "南", "西", "北", "白", "發", "中"
    };

    /** Unicode 麻將字元 (U+1F000 區塊)。 */
    private static final String[] UNICODE = {
            "🀇", "🀈", "🀉", "🀊", "🀋",
            "🀌", "🀍", "🀎", "🀏",
            "🀙", "🀚", "🀛", "🀜", "🀝",
            "🀞", "🀟", "🀠", "🀡",
            "🀐", "🀑", "🀒", "🀓", "🀔",
            "🀕", "🀖", "🀗", "🀘",
            "🀀", "🀁", "🀂", "🀃",
            "🀆", "🀅", "🀄"
    };

    static {
        for (int i = 0; i < KINDS; i++) {
            NORMAL[i] = new Tile(i, false);
            RED[i] = new Tile(i, true);
        }
    }

    private final int id;
    private final boolean red;

    private Tile(int id, boolean red) {
        this.id = id;
        this.red = red;
    }

    public static Tile of(int id) {
        checkId(id);
        return NORMAL[id];
    }

    /** 赤牌 (紅五)。 */
    public static Tile red(int id) {
        checkId(id);
        return RED[id];
    }

    public static Tile of(Suit suit, int number) {
        return of(idOf(suit, number));
    }

    public static int idOf(Suit suit, int number) {
        return switch (suit) {
            case MAN -> {
                requireRange(number, 1, 9);
                yield MAN_START + number - 1;
            }
            case PIN -> {
                requireRange(number, 1, 9);
                yield PIN_START + number - 1;
            }
            case SOU -> {
                requireRange(number, 1, 9);
                yield SOU_START + number - 1;
            }
            case HONOR -> {
                requireRange(number, 1, 7);
                yield HONOR_START + number - 1;
            }
        };
    }

    private static void requireRange(int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Number out of range: " + value);
        }
    }

    private static void checkId(int id) {
        if (id < 0 || id >= KINDS) {
            throw new IllegalArgumentException("Tile id out of range: " + id);
        }
    }

    /**
     * 解析牌的簡寫，例如 {@code 1m}、{@code 5p}、{@code 0s} (赤五索)、{@code 1z} (東)。
     */
    public static Tile parse(String text) {
        String t = text.trim().toLowerCase();
        if (t.length() != 2) {
            throw new IllegalArgumentException("Invalid tile: " + text);
        }
        int number = t.charAt(0) - '0';
        Suit suit = Suit.fromCode(t.charAt(1));
        if (number == 0) {
            if (suit == Suit.HONOR) {
                throw new IllegalArgumentException("Honors have no red tile: " + text);
            }
            return red(idOf(suit, 5));
        }
        return of(idOf(suit, number));
    }

    /**
     * 解析一整手牌的簡寫，例如 {@code 123m456p789s11122z}。
     */
    public static List<Tile> parseHand(String text) {
        List<Tile> tiles = new ArrayList<>();
        List<Integer> pending = new ArrayList<>();
        for (char c : text.trim().toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (Character.isDigit(c)) {
                pending.add(c - '0');
                continue;
            }
            Suit suit = Suit.fromCode(Character.toLowerCase(c));
            for (int number : pending) {
                tiles.add(number == 0 ? red(idOf(suit, 5)) : of(idOf(suit, number)));
            }
            pending.clear();
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("Dangling digits in: " + text);
        }
        return tiles;
    }

    /** 把牌陣列轉為 34 種牌的計數。 */
    public static int[] toCounts(Iterable<Tile> tiles) {
        int[] counts = new int[KINDS];
        for (Tile tile : tiles) {
            counts[tile.id]++;
        }
        return counts;
    }

    public int id() {
        return id;
    }

    public boolean isRed() {
        return red;
    }

    public Suit suit() {
        if (id >= HONOR_START) {
            return Suit.HONOR;
        }
        if (id >= SOU_START) {
            return Suit.SOU;
        }
        if (id >= PIN_START) {
            return Suit.PIN;
        }
        return Suit.MAN;
    }

    /** 數牌回傳 1..9；字牌回傳 1..7 (東=1 ... 中=7)。 */
    public int number() {
        return switch (suit()) {
            case MAN -> id - MAN_START + 1;
            case PIN -> id - PIN_START + 1;
            case SOU -> id - SOU_START + 1;
            case HONOR -> id - HONOR_START + 1;
        };
    }

    public boolean isHonor() {
        return id >= HONOR_START;
    }

    public boolean isWind() {
        return id >= EAST && id <= NORTH;
    }

    public boolean isDragon() {
        return id >= HAKU && id <= CHUN;
    }

    /** 老頭牌 (1 或 9)。 */
    public boolean isTerminal() {
        return !isHonor() && (number() == 1 || number() == 9);
    }

    /** 么九牌 (老頭牌或字牌)。 */
    public boolean isTerminalOrHonor() {
        return isHonor() || isTerminal();
    }

    /** 中張牌 (2..8)。 */
    public boolean isSimple() {
        return !isTerminalOrHonor();
    }

    /** 綠一色可用的牌 (23468索 + 發)。 */
    public boolean isGreen() {
        if (id == HATSU) {
            return true;
        }
        if (suit() != Suit.SOU) {
            return false;
        }
        return switch (number()) {
            case 2, 3, 4, 6, 8 -> true;
            default -> false;
        };
    }

    /** 作為寶牌指示牌時，指示出的寶牌。 */
    public Tile doraFromIndicator() {
        if (isHonor()) {
            if (isWind()) {
                return of(EAST + (id - EAST + 1) % 4);
            }
            return of(HAKU + (id - HAKU + 1) % 3);
        }
        int base = id - (number() - 1);
        return of(base + (number() % 9));
    }

    public String displayName() {
        return red ? "赤" + NAMES[id] : NAMES[id];
    }

    public String unicode() {
        return UNICODE[id];
    }

    /** 簡寫，例如 {@code 5m}、赤五萬為 {@code 0m}。 */
    public String shortName() {
        Suit suit = suit();
        return (red ? 0 : number()) + String.valueOf(suit.code());
    }

    @Override
    public int compareTo(Tile other) {
        if (id != other.id) {
            return Integer.compare(id, other.id);
        }
        return Boolean.compare(other.red, red);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Tile tile && tile.id == id && tile.red == red;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, red);
    }

    @Override
    public String toString() {
        return shortName();
    }
}
