package com.majong.riichi.core;

/** A yaku together with what it contributed to the hand. */
public record ScoredYaku(Yaku yaku, int han) {

    public String display() {
        if (yaku.isYakuman()) {
            return yaku.display() + (yaku.yakumanMultiplier() > 1 ? "（雙倍役滿）" : "（役滿）");
        }
        return yaku.display() + " " + han + "翻";
    }
}
