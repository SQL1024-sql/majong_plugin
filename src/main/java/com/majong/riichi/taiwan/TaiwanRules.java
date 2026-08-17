package com.majong.riichi.taiwan;

import java.util.EnumMap;
import java.util.Map;

/**
 * The stakes a taiwanese table plays for, and what each pattern is worth.
 *
 * <p>A win is paid as the base plus the tai count times the tai value, which is
 * where the usual shorthand "thirty base, ten tai" comes from.
 */
public final class TaiwanRules {

    public static final int DEFAULT_BASE = 30;
    public static final int DEFAULT_PER_TAI = 10;

    private final int base;
    private final int perTai;
    private final Map<Tai, Integer> values;

    public TaiwanRules(int base, int perTai, Map<Tai, Integer> overrides) {
        this.base = base;
        this.perTai = perTai;
        this.values = new EnumMap<>(Tai.class);
        for (Tai tai : Tai.values()) {
            this.values.put(tai, tai.defaultTai());
        }
        if (overrides != null) {
            this.values.putAll(overrides);
        }
    }

    /** The common Taiwanese stake of thirty base and ten a tai. */
    public static TaiwanRules standard() {
        return new TaiwanRules(DEFAULT_BASE, DEFAULT_PER_TAI, null);
    }

    public int base() {
        return base;
    }

    public int perTai() {
        return perTai;
    }

    public int valueOf(Tai tai) {
        return values.getOrDefault(tai, tai.defaultTai());
    }

    /** What one losing player hands over for a win worth the given tai. */
    public int payment(int tai) {
        return base + tai * perTai;
    }
}
