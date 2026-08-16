package com.majong.riichi.plugin.scene;

import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
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
 * Lays a row of tiles out in the world to check how they look and how they
 * click.
 *
 * <p>The orientation was settled by trying every combination in game: giving the
 * display entity the same yaw as the player turns the tile's face towards them,
 * and the item display transform makes no difference because the model carries
 * no display block of its own.
 */
public final class TilePreview implements Listener {

    /** A full hand, so spacing and legibility can be judged at a realistic width. */
    private static final List<String> SAMPLE = List.of(
            "1m", "2m", "3m", "4p", "5p", "0p", "7s", "8s", "9s", "1z", "1z", "5z", "6z", "7z");

    public static final float DEFAULT_SCALE = 0.5f;
    public static final double DEFAULT_SPACING = 0.22;
    private static final double DISTANCE = 1.6;
    private static final double HEIGHT = 1.4;

    private final Plugin plugin;
    private final NamespacedKey tileKey;
    private final Map<UUID, List<Entity>> spawned = new HashMap<>();

    public TilePreview(Plugin plugin) {
        this.plugin = plugin;
        this.tileKey = new NamespacedKey(plugin, "preview_tile");
    }

    /** Spawns the row in front of the player, replacing any already there. */
    public void show(Player player, float scale, double spacing) {
        clear(player);
        Location eye = player.getLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());

        Location origin = eye.clone().add(forward.clone().multiply(DISTANCE));
        origin.setY(eye.getY() + HEIGHT);

        double tileHeight = 8.0 / 16.0 * scale;
        List<Entity> entities = new ArrayList<>();
        for (int index = 0; index < SAMPLE.size(); index++) {
            double offset = (index - (SAMPLE.size() - 1) / 2.0) * spacing;
            Location at = origin.clone().add(right.clone().multiply(offset));
            at.setYaw(eye.getYaw());
            at.setPitch(0f);
            Tile tile = Tile.parse(SAMPLE.get(index));
            entities.add(spawnTile(at, tile, scale));
            entities.add(spawnHitbox(at, tile, spacing, tileHeight));
        }
        spawned.put(player.getUniqueId(), entities);
    }

    private ItemDisplay spawnTile(Location at, Tile tile, float scale) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(TileModels.of(tile));
            display.setTransformation(scaled(scale));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
        });
    }

    private Interaction spawnHitbox(Location at, Tile tile, double spacing, double tileHeight) {
        // An interaction is anchored at the bottom of its box, the display at its centre.
        Location base = at.clone();
        base.setY(at.getY() - tileHeight / 2);
        return at.getWorld().spawn(base, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth((float) (spacing * 0.9));
            hitbox.setInteractionHeight((float) tileHeight);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            hitbox.getPersistentDataContainer().set(tileKey, PersistentDataType.STRING,
                    tile.notation());
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

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        String notation = event.getRightClicked().getPersistentDataContainer()
                .get(tileKey, PersistentDataType.STRING);
        if (notation == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text()
                .append(Component.text("[預覽] ", NamedTextColor.GOLD))
                .append(Component.text("點到 ", NamedTextColor.WHITE))
                .append(Component.text(Tiles.display(Tile.parse(notation).kind()),
                        NamedTextColor.YELLOW))
                .append(Component.text(" (" + notation + ")", NamedTextColor.GRAY))
                .build());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }
}
