package io.github.sql1024.mahjong.bukkit;

import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.Tile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 把麻將牌畫成聊天文字與物品。 */
public final class TileRender {

    private TileRender() {
    }

    public static NamedTextColor color(Tile tile) {
        if (tile.isRed()) {
            return NamedTextColor.GOLD;
        }
        return switch (tile.suit()) {
            case MAN -> NamedTextColor.RED;
            case PIN -> NamedTextColor.AQUA;
            case SOU -> NamedTextColor.GREEN;
            case HONOR -> NamedTextColor.WHITE;
        };
    }

    /** 聊天用的單張牌，例如「🀋五萬」。 */
    public static Component text(Tile tile) {
        return Component.text(tile.unicode() + tile.displayName(), color(tile))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component text(List<Tile> tiles) {
        Component component = Component.empty();
        for (int i = 0; i < tiles.size(); i++) {
            if (i > 0) {
                component = component.append(Component.text(" "));
            }
            component = component.append(text(tiles.get(i)));
        }
        return component;
    }

    public static Component text(Meld meld) {
        return Component.text(meld.type().displayName() + " ", NamedTextColor.YELLOW)
                .append(text(meld.tiles()))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Material material(Tile tile) {
        return switch (tile.suit()) {
            case MAN -> Material.BRICK;
            case PIN -> Material.CLAY_BALL;
            case SOU -> Material.BAMBOO;
            case HONOR -> tile.isDragon() ? Material.GLOWSTONE_DUST : Material.PAPER;
        };
    }

    /** 產生一張牌的物品，數牌用堆疊數量表示點數。 */
    public static ItemStack item(Tile tile, List<Component> extraLore) {
        ItemStack stack = new ItemStack(material(tile));
        stack.setAmount(tile.isHonor() ? 1 : Math.max(1, tile.number()));
        stack.editMeta(meta -> {
            Component name = Component.text(tile.unicode() + " " + tile.displayName(), color(tile));
            if (tile.isRed()) {
                name = name.decoration(TextDecoration.BOLD, true);
            }
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(tile.shortName(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (extraLore != null) {
                lore.addAll(extraLore);
            }
            meta.lore(lore);
        });
        return stack;
    }

    public static ItemStack item(Tile tile) {
        return item(tile, null);
    }

    /** 產生一個按鈕物品。 */
    public static ItemStack button(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null) {
                List<Component> lines = new ArrayList<>();
                for (Component line : lore) {
                    lines.add(line.decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lines);
            }
        });
        return stack;
    }

    public static Component label(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
