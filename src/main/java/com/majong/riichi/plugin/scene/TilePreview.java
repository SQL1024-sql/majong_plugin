package com.majong.riichi.plugin.scene;

import com.majong.riichi.core.Tile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Puts a few tiles in the world so their model, orientation and hitbox can be
 * checked by eye.
 *
 * <p>Nothing here can be verified without a client, so the preview lays the same
 * tiles out four times, once per combination of item display transform and
 * facing. One screenshot then says which combination stands the tiles up the
 * right way round, and the rest of the scene work can be built on it.
 */
public final class TilePreview implements Listener {

    /** A spread of tiles that shows off each suit, an honour and a red five. */
    private static final List<String> SAMPLE = List.of("1m", "5p", "0p", "9s", "1z", "6z", "7z");

    private static final float TILE_SCALE = 0.5f;
    private static final double SPACING = 0.22;
    private static final double ROW_GAP = 0.45;
    private static final double DISTANCE = 2.0;
    /** Tile height in blocks once scaled: eight model units of a sixteen unit cube. */
    private static final double TILE_HEIGHT = 8.0 / 16.0 * TILE_SCALE;

    /** The combinations being compared. */
    private record Variant(String label, ItemDisplay.ItemDisplayTransform transform, float yawOffset) {
    }

    private static final List<Variant> VARIANTS = List.of(
            new Variant("A  FIXED  yaw+0", ItemDisplay.ItemDisplayTransform.FIXED, 0f),
            new Variant("B  NONE   yaw+0", ItemDisplay.ItemDisplayTransform.NONE, 0f),
            new Variant("C  FIXED  yaw+180", ItemDisplay.ItemDisplayTransform.FIXED, 180f),
            new Variant("D  NONE   yaw+180", ItemDisplay.ItemDisplayTransform.NONE, 180f));

    private final Plugin plugin;
    private final NamespacedKey tileKey;
    private final NamespacedKey rowKey;
    private final Map<UUID, List<Entity>> spawned = new HashMap<>();

    public TilePreview(Plugin plugin) {
        this.plugin = plugin;
        this.tileKey = new NamespacedKey(plugin, "preview_tile");
        this.rowKey = new NamespacedKey(plugin, "preview_row");
    }

    /** Spawns the four rows in front of the player, replacing any already there. */
    public void show(Player player) {
        clear(player);
        Location eye = player.getLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        Location origin = eye.clone().add(forward.clone().multiply(DISTANCE));
        origin.setY(eye.getY() + 1.6);

        List<Entity> entities = new ArrayList<>();
        for (int row = 0; row < VARIANTS.size(); row++) {
            Variant variant = VARIANTS.get(row);
            double y = origin.getY() - row * ROW_GAP;
            spawnLabel(player, entities, origin, right, y, variant);
            for (int index = 0; index < SAMPLE.size(); index++) {
                double offset = (index - (SAMPLE.size() - 1) / 2.0) * SPACING;
                Location at = origin.clone().add(right.clone().multiply(offset));
                at.setY(y);
                at.setYaw(eye.getYaw() + variant.yawOffset());
                at.setPitch(0f);
                Tile tile = Tile.parse(SAMPLE.get(index));
                entities.add(spawnTile(at, tile, variant));
                entities.add(spawnHitbox(at, tile, variant));
            }
        }
        spawned.put(player.getUniqueId(), entities);
    }

    private void spawnLabel(Player player, List<Entity> entities, Location origin, Vector right,
                            double y, Variant variant) {
        double offset = (-(SAMPLE.size() - 1) / 2.0 - 1.4) * SPACING;
        Location at = origin.clone().add(right.clone().multiply(offset));
        at.setY(y);
        entities.add(player.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(Component.text(variant.label(), NamedTextColor.YELLOW));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(scaled(0.35f));
        }));
    }

    private ItemDisplay spawnTile(Location at, Tile tile, Variant variant) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(TileModels.of(tile));
            display.setItemDisplayTransform(variant.transform());
            display.setTransformation(scaled(TILE_SCALE));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
        });
    }

    private Interaction spawnHitbox(Location at, Tile tile, Variant variant) {
        // An interaction is anchored at the bottom of its box, the display at its centre.
        Location base = at.clone();
        base.setY(at.getY() - TILE_HEIGHT / 2);
        return at.getWorld().spawn(base, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth((float) (SPACING * 0.9));
            hitbox.setInteractionHeight((float) TILE_HEIGHT);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            hitbox.getPersistentDataContainer().set(tileKey, PersistentDataType.STRING,
                    tile.notation());
            hitbox.getPersistentDataContainer().set(rowKey, PersistentDataType.STRING,
                    variant.label());
        });
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(scale, scale, scale), new AxisAngle4f(0f, 0f, 0f, 1f));
    }

    /** Removes anything this player has spawned. */
    public void clear(Player player) {
        List<Entity> entities = spawned.remove(player.getUniqueId());
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            entity.remove();
        }
    }

    public void clearAll() {
        for (List<Entity> entities : spawned.values()) {
            for (Entity entity : entities) {
                entity.remove();
            }
        }
        spawned.clear();
    }

    public boolean hasPreview(Player player) {
        return spawned.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        var container = event.getRightClicked().getPersistentDataContainer();
        String tile = container.get(tileKey, PersistentDataType.STRING);
        if (tile == null) {
            return;
        }
        event.setCancelled(true);
        String row = container.get(rowKey, PersistentDataType.STRING);
        event.getPlayer().sendMessage(Component.text()
                .append(Component.text("[預覽] ", NamedTextColor.GOLD))
                .append(Component.text("點到 " + tile, NamedTextColor.WHITE))
                .append(Component.text("  (" + row + ")", NamedTextColor.GRAY))
                .build());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }
}
