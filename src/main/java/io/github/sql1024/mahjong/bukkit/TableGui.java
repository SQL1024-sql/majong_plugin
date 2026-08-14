package io.github.sql1024.mahjong.bukkit;

import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.Tile;
import io.github.sql1024.mahjong.game.CallOption;
import io.github.sql1024.mahjong.game.Discard;
import io.github.sql1024.mahjong.game.MahjongTable;
import io.github.sql1024.mahjong.game.Seat;
import io.github.sql1024.mahjong.game.TurnOptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一位玩家看到的牌桌畫面。
 *
 * <pre>
 *  0- 8  場況、寶牌、點數、自己的情報
 *  9-17  上家牌河    18-26 對家牌河    27-35 下家牌河
 * 36-49  自己的手牌
 * 50-53  自摸/榮、立直、槓、說明
 * </pre>
 */
public final class TableGui implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int HAND_START = 36;
    public static final int HAND_SLOTS = 14;
    public static final int BUTTON_WIN = 50;
    public static final int BUTTON_RIICHI = 51;
    public static final int BUTTON_KAN = 52;
    public static final int BUTTON_HELP = 53;

    private final MahjongGame game;
    private final int seatIndex;
    private final Inventory inventory;
    private final Tile[] handSlots = new Tile[HAND_SLOTS];
    private boolean riichiArmed;

    public TableGui(MahjongGame game, int seatIndex) {
        this.game = game;
        this.seatIndex = seatIndex;
        this.inventory = org.bukkit.Bukkit.createInventory(this, SIZE,
                Component.text("日本麻將 — " + game.table().seat(seatIndex).name(), NamedTextColor.DARK_GREEN));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int seatIndex() {
        return seatIndex;
    }

    public MahjongGame game() {
        return game;
    }

    public boolean riichiArmed() {
        return riichiArmed;
    }

    public void setRiichiArmed(boolean armed) {
        this.riichiArmed = armed;
    }

    /** 這個格子對應的手牌，若不是手牌格則為 {@code null}。 */
    public Tile tileAt(int slot) {
        int index = slot - HAND_START;
        if (index < 0 || index >= HAND_SLOTS) {
            return null;
        }
        return handSlots[index];
    }

    public void refresh() {
        MahjongTable table = game.table();
        Seat seat = table.seat(seatIndex);
        inventory.clear();
        Arrays.fill(handSlots, null);

        inventory.setItem(0, roundItem(table));
        List<Tile> indicators = table.wall() == null ? List.of() : table.wall().doraIndicators();
        for (int i = 0; i < 5; i++) {
            if (i < indicators.size()) {
                inventory.setItem(1 + i, TileRender.item(indicators.get(i),
                        List.of(TileRender.label("寶牌指示牌", NamedTextColor.GOLD))));
            } else {
                inventory.setItem(1 + i, TileRender.button(Material.GRAY_STAINED_GLASS_PANE,
                        TileRender.label("未翻開", NamedTextColor.DARK_GRAY), null));
            }
        }
        inventory.setItem(6, scoreItem(table));
        inventory.setItem(7, selfItem(table, seat));
        inventory.setItem(8, meldItem(seat));

        for (int offset = 1; offset <= 3; offset++) {
            Seat other = table.seat((seatIndex + offset) % 4);
            int row = switch (offset) {
                case 3 -> 9;   // 上家
                case 2 -> 18;  // 對家
                default -> 27; // 下家
            };
            renderRiver(row, other);
        }

        List<Tile> hand = new ArrayList<>(seat.hand());
        Tile drawn = seat.drawn();
        for (int i = 0; i < hand.size() && i < HAND_SLOTS; i++) {
            Tile tile = hand.get(i);
            handSlots[i] = tile;
            inventory.setItem(HAND_START + i, TileRender.item(tile, handLore(table, seat, tile, false)));
        }
        if (drawn != null) {
            int index = Math.min(hand.size() + 1, HAND_SLOTS - 1);
            handSlots[index] = drawn;
            inventory.setItem(HAND_START + index, TileRender.item(drawn, handLore(table, seat, drawn, true)));
        }

        renderButtons(table, seat);
    }

    private void renderRiver(int start, Seat other) {
        List<Discard> discards = other.discards();
        int from = Math.max(0, discards.size() - 9);
        for (int i = 0; i < 9; i++) {
            int index = from + i;
            if (index >= discards.size()) {
                break;
            }
            Discard discard = discards.get(index);
            List<Component> lore = new ArrayList<>();
            lore.add(TileRender.label(other.wind().displayName() + " " + other.name() + " 的牌河",
                    NamedTextColor.GRAY));
            if (discard.riichiDeclare()) {
                lore.add(TileRender.label("立直宣言牌", NamedTextColor.GOLD));
            }
            if (discard.called()) {
                lore.add(TileRender.label("已被鳴走", NamedTextColor.DARK_GRAY));
            }
            inventory.setItem(start + i, TileRender.item(discard.tile(), lore));
        }
    }

    private List<Component> handLore(MahjongTable table, Seat seat, Tile tile, boolean drawn) {
        List<Component> lore = new ArrayList<>();
        if (drawn) {
            lore.add(TileRender.label("剛摸到的牌", NamedTextColor.YELLOW));
        }
        if (table.phase() == MahjongTable.Phase.TURN && table.currentSeat() == seatIndex) {
            if (riichiArmed) {
                boolean ok = table.currentOptions().riichiDiscards().stream()
                        .anyMatch(candidate -> candidate.id() == tile.id());
                lore.add(ok
                        ? TileRender.label("點擊：立直並打出", NamedTextColor.GOLD)
                        : TileRender.label("立直後不能打這張", NamedTextColor.RED));
            } else {
                lore.add(TileRender.label("點擊：打出這張牌", NamedTextColor.GREEN));
            }
        }
        return lore;
    }

    private ItemStack roundItem(MahjongTable table) {
        List<Component> lore = new ArrayList<>();
        lore.add(TileRender.label("剩餘 " + (table.wall() == null ? 0 : table.wall().remaining()) + " 張",
                NamedTextColor.GRAY));
        lore.add(TileRender.label("立直棒 " + table.riichiSticks() + " 本", NamedTextColor.GRAY));
        lore.add(TileRender.label("莊家：" + table.seat(table.dealerSeat()).name(), NamedTextColor.GRAY));
        return TileRender.button(Material.BOOK,
                TileRender.label(table.roundName(), NamedTextColor.GREEN), lore);
    }

    private ItemStack scoreItem(MahjongTable table) {
        List<Component> lore = new ArrayList<>();
        for (Seat other : table.seats()) {
            String marker = other.index() == table.dealerSeat() ? "(莊) " : "";
            NamedTextColor color = other.index() == seatIndex ? NamedTextColor.YELLOW : NamedTextColor.WHITE;
            lore.add(TileRender.label(marker + other.wind().displayName() + " " + other.name()
                    + " — " + other.points() + " 點" + (other.riichi() ? " [立直]" : ""), color));
        }
        return TileRender.button(Material.GOLD_INGOT,
                TileRender.label("點數", NamedTextColor.GOLD), lore);
    }

    private ItemStack selfItem(MahjongTable table, Seat seat) {
        List<Component> lore = new ArrayList<>();
        lore.add(TileRender.label("座位風：" + seat.wind().displayName(), NamedTextColor.GRAY));
        if (seat.isTenpai()) {
            StringBuilder waits = new StringBuilder();
            for (int id : seat.waits()) {
                waits.append(Tile.of(id).displayName()).append(' ');
            }
            lore.add(TileRender.label("聽：" + waits.toString().trim(), NamedTextColor.AQUA));
            if (seat.isFuriten()) {
                lore.add(TileRender.label("振聽中，不能榮和", NamedTextColor.RED));
            }
        } else {
            lore.add(TileRender.label("尚未聽牌", NamedTextColor.GRAY));
        }
        StringBuilder river = new StringBuilder();
        for (Discard discard : seat.discards()) {
            river.append(discard.tile().shortName()).append(' ');
        }
        lore.add(TileRender.label("自己的牌河：" + river.toString().trim(), NamedTextColor.DARK_GRAY));
        return TileRender.button(Material.PRISMARINE_SHARD,
                TileRender.label(seat.name() + " 的情報", NamedTextColor.AQUA), lore);
    }

    private ItemStack meldItem(Seat seat) {
        List<Component> lore = new ArrayList<>();
        if (seat.melds().isEmpty()) {
            lore.add(TileRender.label("門前清", NamedTextColor.GREEN));
        } else {
            for (Meld meld : seat.melds()) {
                lore.add(TileRender.text(meld));
            }
        }
        return TileRender.button(Material.EMERALD,
                TileRender.label("副露", NamedTextColor.LIGHT_PURPLE), lore);
    }

    private void renderButtons(MahjongTable table, Seat seat) {
        boolean myTurn = table.phase() == MahjongTable.Phase.TURN && table.currentSeat() == seatIndex;
        TurnOptions options = table.currentOptions();

        if (myTurn && options.canTsumo()) {
            inventory.setItem(BUTTON_WIN, TileRender.button(Material.NETHER_STAR,
                    TileRender.label("自摸！", NamedTextColor.GOLD),
                    List.of(TileRender.label("點擊宣告自摸", NamedTextColor.GRAY))));
        } else if (table.phase() == MahjongTable.Phase.CALLS
                && table.pendingOptions(seatIndex).stream()
                .anyMatch(option -> option.type() == io.github.sql1024.mahjong.game.CallType.RON)) {
            inventory.setItem(BUTTON_WIN, TileRender.button(Material.NETHER_STAR,
                    TileRender.label("榮和！", NamedTextColor.GOLD),
                    List.of(TileRender.label("點擊宣告榮和", NamedTextColor.GRAY))));
        }

        if (myTurn && options.canRiichi()) {
            List<Component> lore = new ArrayList<>();
            lore.add(TileRender.label(riichiArmed ? "已進入立直模式，選一張要打的牌" : "點擊後選擇要打出的牌",
                    NamedTextColor.GRAY));
            for (Tile tile : options.riichiDiscards()) {
                lore.add(TileRender.text(tile));
            }
            inventory.setItem(BUTTON_RIICHI, TileRender.button(
                    riichiArmed ? Material.LIME_DYE : Material.SUNFLOWER,
                    TileRender.label("立直 (-1000)", NamedTextColor.YELLOW), lore));
        }

        if (myTurn && !options.kanOptions().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (CallOption option : options.kanOptions()) {
                lore.add(TileRender.label(option.describe(), NamedTextColor.GRAY));
            }
            inventory.setItem(BUTTON_KAN, TileRender.button(Material.IRON_INGOT,
                    TileRender.label("槓", NamedTextColor.WHITE), lore));
        }

        if (myTurn && options.canKyuushu()) {
            inventory.setItem(BUTTON_HELP, TileRender.button(Material.BARRIER,
                    TileRender.label("九種九牌 (流局)", NamedTextColor.RED),
                    List.of(TileRender.label("點擊宣告途中流局", NamedTextColor.GRAY))));
            return;
        }

        inventory.setItem(BUTTON_HELP, TileRender.button(Material.CLOCK,
                TileRender.label("操作說明", NamedTextColor.WHITE),
                List.of(
                        TileRender.label("點手牌 = 打出該張", NamedTextColor.GRAY),
                        TileRender.label("鳴牌請點聊天欄的按鈕", NamedTextColor.GRAY),
                        TileRender.label("指令：/mj help", NamedTextColor.GRAY))));
    }
}
