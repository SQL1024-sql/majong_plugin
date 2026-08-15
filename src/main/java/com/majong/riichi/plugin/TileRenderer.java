package com.majong.riichi.plugin;

import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;

/** The two ways a tile can be drawn in chat. */
public enum TileRenderer {

    /** Coloured text such as {@code 3m} or {@code 東}; works without a resource pack. */
    TEXT {
        @Override
        public Component render(Tile tile) {
            NamedTextColor colour = switch (Tiles.suitOf(tile.kind())) {
                case MAN -> NamedTextColor.RED;
                case PIN -> NamedTextColor.AQUA;
                case SOU -> NamedTextColor.GREEN;
                case HONOR -> NamedTextColor.GOLD;
            };
            Component text = Component.text(Tiles.display(tile.kind()), colour);
            if (tile.red()) {
                text = text.decorate(TextDecoration.BOLD).color(NamedTextColor.LIGHT_PURPLE);
            }
            return text;
        }
    },

    /** Drawn tiles from the bundled resource pack. */
    GLYPH {
        @Override
        public Component render(Tile tile) {
            // The glyphs carry their own colours, so the text has to stay white
            // and shadowless or the client tints them.
            return Component.text(String.valueOf(glyph(tile)))
                    .font(FONT)
                    .color(NamedTextColor.WHITE)
                    .shadowColor(ShadowColor.none());
        }
    };

    /** The font the tile glyphs live in. */
    public static final Key FONT = Key.key("majong", "tiles");

    /** Codepoint of the first tile; the 34 kinds follow in order. */
    private static final char FIRST_TILE = '\uE000';
    /** The three red fives sit just past the ordinary tiles. */
    private static final char FIRST_RED_FIVE = '\uE022';

    public abstract Component render(Tile tile);

    public boolean isGraphical() {
        return this == GLYPH;
    }

    /** The private use codepoint a tile is drawn at. */
    public static char glyph(Tile tile) {
        if (tile.red()) {
            return switch (Tiles.suitOf(tile.kind())) {
                case MAN -> FIRST_RED_FIVE;
                case PIN -> (char) (FIRST_RED_FIVE + 1);
                case SOU -> (char) (FIRST_RED_FIVE + 2);
                case HONOR -> throw new IllegalArgumentException("honours are never red");
            };
        }
        return (char) (FIRST_TILE + tile.kind());
    }

    /** What a player wants to see, before working out whether they can see it. */
    public enum Style {
        /** Drawn tiles for players who loaded the resource pack, text for the rest. */
        AUTO,
        /** Always drawn tiles. */
        TILES,
        /** Always coloured text. */
        TEXT;

        /** Reads the value of the {@code tile-graphics} setting, defaulting to auto. */
        public static Style parse(String name) {
            if (name == null) {
                return AUTO;
            }
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "tiles", "graphic", "graphics", "always" -> TILES;
                case "text", "never", "off" -> TEXT;
                default -> AUTO;
            };
        }

        /** How this preference reads in chat. */
        public String display() {
            return switch (this) {
                case AUTO -> "自動";
                case TILES -> "牌圖";
                case TEXT -> "文字";
            };
        }
    }
}
