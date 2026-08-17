package com.majong.riichi.core;

/**
 * The eight flowers a taiwanese set adds on top of the 136 ordinary tiles.
 *
 * <p>A flower never joins a set. It is turned face up as soon as it is drawn,
 * the player takes a replacement tile, and at the end it is worth tai. Each
 * flower belongs to a seat, and a player holding the flower matching their own
 * seat scores for it.
 */
public enum Flower {
    SPRING("春", 0, true),
    SUMMER("夏", 1, true),
    AUTUMN("秋", 2, true),
    WINTER("冬", 3, true),
    PLUM("梅", 0, false),
    ORCHID("蘭", 1, false),
    CHRYSANTHEMUM("菊", 2, false),
    BAMBOO("竹", 3, false);

    private final String display;
    private final int seat;
    private final boolean season;

    Flower(String display, int seat, boolean season) {
        this.display = display;
        this.seat = seat;
        this.season = season;
    }

    public String display() {
        return display;
    }

    /** The seat this flower belongs to, counted from the dealer. */
    public int seat() {
        return seat;
    }

    /** True for the four seasons, false for the four plants. */
    public boolean isSeason() {
        return season;
    }

    /** The name used in the resource pack, {@code f1} through {@code f8}. */
    public String modelName() {
        return "f" + (ordinal() + 1);
    }

    /** True when this flower matches the given seat, which is what scores. */
    public boolean matches(int seatIndex) {
        return seat == seatIndex;
    }

    public static Flower[] all() {
        return values();
    }
}
