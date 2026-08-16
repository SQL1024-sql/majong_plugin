package com.majong.riichi.plugin.scene;

import com.majong.riichi.core.Hand;
import com.majong.riichi.core.Meld;
import com.majong.riichi.core.Tile;
import com.majong.riichi.game.RiichiGame;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Draws a table's hands as tiles standing in the world.
 *
 * <p>Seats are arranged round an anchor, each one facing outwards so the player
 * sitting there reads their own hand and everybody else sees the backs. A
 * player's own tiles are drawn twice, faces that only they can see and backs
 * that only the others can, which is what stops someone walking round the table
 * and reading a hand over its owner's shoulder.
 */
public final class TableScene {

    /** How far each seat sits from the middle of the table. */
    private final double radius;
    /** How high the tiles stand above the anchor. */
    private final double height;
    private final float scale;
    private final double spacing;

    private final Plugin plugin;
    private final NamespacedKey tileKey;
    private final NamespacedKey seatKey;

    private Location anchor;
    private final List<Entity> entities = new ArrayList<>();

    public TableScene(Plugin plugin, double radius, double height, float scale, double spacing) {
        this.plugin = plugin;
        this.radius = radius;
        this.height = height;
        this.scale = scale;
        this.spacing = spacing;
        this.tileKey = new NamespacedKey(plugin, "table_tile");
        this.seatKey = new NamespacedKey(plugin, "table_seat");
    }

    public NamespacedKey tileKey() {
        return tileKey;
    }

    public NamespacedKey seatKey() {
        return seatKey;
    }

    public Location anchor() {
        return anchor == null ? null : anchor.clone();
    }

    /**
     * The gap between the player and their own row of tiles, on top of the
     * radius, so the hand sits within reach rather than underfoot.
     */
    private static final double REACH = 0.7;

    /**
     * Places the table in front of the given player, with the first seat between
     * them and the middle so their own tiles face back at them.
     */
    public void placeFor(Location viewer) {
        Vector forward = viewer.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Location centre = viewer.clone().add(forward.multiply(radius + REACH));
        // The anchor yaw points from the middle out towards the first seat, which
        // is the opposite of the way the player is looking.
        centre.setYaw(viewer.getYaw() + 180f);
        centre.setPitch(0f);
        setAnchor(centre);
    }

    /** Places the table around the given middle point. */
    public void setAnchor(Location location) {
        clear();
        this.anchor = location.clone();
    }

    public boolean isPlaced() {
        return anchor != null;
    }

    /**
     * Rebuilds every tile from the current game state. Cheap enough to call
     * after each action: a table is a few dozen display entities.
     */
    public void refresh(RiichiGame game, UUID[] owners) {
        clear();
        if (anchor == null || game == null) {
            return;
        }
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            renderSeat(game, seat, owners[seat]);
        }
    }

    private void renderSeat(RiichiGame game, int seat, UUID owner) {
        float seatYaw = anchor.getYaw() + seat * 90f;
        Vector out = directionOf(seatYaw);
        // The player at this seat looks inwards, so their right hand runs this way.
        Vector right = new Vector(out.getZ(), 0, -out.getX());

        Location centre = anchor.clone().add(out.clone().multiply(radius));
        centre.setY(anchor.getY() + height);
        // A tile shows its face to somebody looking along the tile's own yaw, so
        // pointing the face outwards means facing back the way the seat looks.
        float tileYaw = seatYaw + 180f;

        Hand hand = game.seat(seat).hand();
        List<Tile> concealed = hand.concealed();
        Tile drawn = hand.drawn();
        int count = concealed.size() + (drawn == null ? 0 : 1);

        for (int index = 0; index < concealed.size(); index++) {
            double offset = (index - (count - 1) / 2.0) * spacing;
            place(centre, right, offset, tileYaw, concealed.get(index), seat, owner);
        }
        if (drawn != null) {
            // The tile just drawn sits a little apart, the way it would in hand.
            double offset = (concealed.size() - (count - 1) / 2.0) * spacing + spacing * 0.6;
            place(centre, right, offset, tileYaw, drawn, seat, owner);
        }

        // Melds are public, so they need no hiding and sit off to the side.
        double meldStart = (count - (count - 1) / 2.0) * spacing + spacing * 1.6;
        for (Meld meld : hand.melds()) {
            for (Tile tile : meld.tiles()) {
                Location at = position(centre, right, meldStart, tileYaw);
                entities.add(spawnFace(at, tile));
                meldStart += spacing;
            }
            meldStart += spacing * 0.4;
        }
    }

    private void place(Location centre, Vector right, double offset, float tileYaw,
                       Tile tile, int seat, UUID owner) {
        Location at = position(centre, right, offset, tileYaw);
        if (owner == null) {
            // Nobody to keep it from, so a plain back is enough.
            entities.add(spawnBack(at));
            return;
        }
        ItemDisplay face = spawnFace(at, tile);
        ItemDisplay back = spawnBack(at);
        entities.add(face);
        entities.add(back);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(owner)) {
                player.hideEntity(plugin, back);
            } else {
                player.hideEntity(plugin, face);
            }
        }
        entities.add(spawnHitbox(at, tile, seat));
    }

    private Location position(Location centre, Vector right, double offset, float tileYaw) {
        Location at = centre.clone().add(right.clone().multiply(offset));
        at.setYaw(tileYaw);
        at.setPitch(0f);
        return at;
    }

    private ItemDisplay spawnFace(Location at, Tile tile) {
        return spawnDisplay(at, TileModels.of(tile));
    }

    private ItemDisplay spawnBack(Location at) {
        return spawnDisplay(at, TileModels.back());
    }

    private ItemDisplay spawnDisplay(Location at, org.bukkit.inventory.ItemStack stack) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
        });
    }

    private Interaction spawnHitbox(Location at, Tile tile, int seat) {
        double tileHeight = 8.0 / 16.0 * scale;
        Location base = at.clone();
        base.setY(at.getY() - tileHeight / 2);
        return at.getWorld().spawn(base, Interaction.class, hitbox -> {
            hitbox.setInteractionWidth((float) (spacing * 0.85));
            hitbox.setInteractionHeight((float) tileHeight);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            hitbox.getPersistentDataContainer().set(tileKey, PersistentDataType.STRING,
                    tile.notation());
            hitbox.getPersistentDataContainer().set(seatKey, PersistentDataType.INTEGER, seat);
        });
    }

    public void clear() {
        for (Entity entity : entities) {
            entity.remove();
        }
        entities.clear();
    }

    /** The unit vector a yaw points along, in Minecraft's convention. */
    private static Vector directionOf(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0, Math.cos(radians));
    }
}
