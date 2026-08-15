package com.majong.riichi.plugin.scene;

import com.majong.riichi.core.Tile;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Builds the item stacks that carry the three dimensional tile models.
 *
 * <p>The base material is irrelevant: the {@code item_model} component replaces
 * the model entirely, so every tile is a piece of paper wearing a tile.
 */
public final class TileModels {

    private static final String NAMESPACE = "majong";
    private static final Material BASE = Material.PAPER;

    /** The model shown for a tile nobody is allowed to see. */
    public static final Key BACK = Key.key(NAMESPACE, "tile_back");

    private TileModels() {
    }

    /** The model key for a tile, for example {@code majong:tile_3m}. */
    public static Key modelKey(Tile tile) {
        return Key.key(NAMESPACE, "tile_" + tile.notation());
    }

    public static ItemStack of(Tile tile) {
        return withModel(modelKey(tile));
    }

    public static ItemStack back() {
        return withModel(BACK);
    }

    public static ItemStack withModel(Key model) {
        ItemStack stack = ItemStack.of(BASE);
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }
}
