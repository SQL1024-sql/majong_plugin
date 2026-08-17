package com.majong.riichi.plugin;

import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.Action;
import com.majong.riichi.game.RiichiGame;
import com.majong.riichi.game.Variant;
import com.majong.riichi.plugin.scene.TilePreview;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Handles {@code /mahjong} and its aliases. */
public final class MahjongCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "join", "leave", "bots", "start", "list", "info", "hand",
            "discard", "riichi", "tsumo", "ron", "pon", "chi", "kan", "pass", "kyuushu", "style",
            "preview", "table");

    private final MahjongPlugin plugin;

    MahjongCommand(MahjongPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("majong.admin")) {
                sender.sendMessage(error("你沒有權限重新載入設定。"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(info("設定已重新載入。"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("只有玩家可以使用麻將指令。"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(player, args.length > 1 ? args[1] : player.getName(),
                    Variant.parse(args.length > 2 ? args[2] : plugin.defaultVariant().name()));
            case "join" -> join(player, args.length > 1 ? args[1] : null);
            case "leave" -> leave(player);
            case "bots", "bot" -> addBots(player);
            case "start" -> start(player);
            case "list" -> list(player);
            case "info" -> info(player);
            case "hand" -> hand(player);
            case "style" -> style(player, args);
            case "preview" -> preview(player, args);
            case "table" -> placeTable(player);
            case "discard" -> discard(player, args, false);
            case "riichi" -> discard(player, args, true);
            case "tsumo" -> submitSimple(player, new Action.Tsumo());
            case "ron" -> submitSimple(player, new Action.Ron());
            case "pon" -> submitSimple(player, new Action.Pon());
            case "pass", "skip" -> submitSimple(player, new Action.Pass());
            case "kyuushu" -> submitSimple(player, new Action.NineTerminals());
            case "chi" -> chi(player, args);
            case "kan" -> kan(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    // ------------------------------------------------------------- lobbying

    private void create(Player player, String name, Variant variant) {
        Table table = plugin.tables().create(player, name, variant);
        if (table == null) {
            player.sendMessage(error("桌名已被使用，或你已經在其他桌了。"));
            return;
        }
        player.sendMessage(info("已開桌「" + table.name() + "」（" + variant.display()
                + "）。用 /mj bots 補電腦，或等其他人 /mj join " + table.name() + "。"));
    }

    private void join(Player player, String name) {
        Table table = name == null ? firstOpenTable() : plugin.tables().get(name);
        if (table == null) {
            player.sendMessage(error("找不到可加入的桌子。"));
            return;
        }
        int seat = plugin.tables().join(player, table);
        if (seat < 0) {
            player.sendMessage(error("這桌已滿、已開局，或你已經在其他桌了。"));
            return;
        }
        table.broadcast(info(player.getName() + " 入座「" + table.name() + "」，尚缺 "
                + table.freeSeats() + " 人。"));
    }

    private Table firstOpenTable() {
        for (Table table : plugin.tables().tables()) {
            if (table.state() == Table.State.LOBBY && table.freeSeats() > 0) {
                return table;
            }
        }
        return null;
    }

    private void leave(Player player) {
        Table table = plugin.tables().leave(player);
        if (table == null) {
            player.sendMessage(error("你目前不在任何桌上。"));
            return;
        }
        player.sendMessage(info("已離開「" + table.name() + "」。"));
    }

    private void addBots(Player player) {
        Table table = requireTable(player);
        if (table == null) {
            return;
        }
        if (table.state() != Table.State.LOBBY) {
            player.sendMessage(error("開局後不能再補電腦。"));
            return;
        }
        int added = table.fillWithBots();
        player.sendMessage(info("補了 " + added + " 位電腦玩家，輸入 /mj start 開始。"));
    }

    private void start(Player player) {
        Table table = requireTable(player);
        if (table == null) {
            return;
        }
        if (!player.getUniqueId().equals(table.owner())) {
            player.sendMessage(error("只有開桌的人可以開局。"));
            return;
        }
        if (!table.start(plugin.rulesFor(table.variant()))) {
            player.sendMessage(error("還沒坐滿四家，或這桌已經開局了。"));
        }
    }

    private void list(Player player) {
        if (plugin.tables().tables().isEmpty()) {
            player.sendMessage(info("目前沒有牌桌，用 /mj create 開一桌。"));
            return;
        }
        player.sendMessage(info("牌桌一覽："));
        for (Table table : plugin.tables().tables()) {
            player.sendMessage(Component.text("  " + table.name() + " — "
                    + switch (table.state()) {
                        case LOBBY -> table.variant().display() + "，等待中，尚缺 "
                                + table.freeSeats() + " 人";
                        case PLAYING -> "對局中";
                        case FINISHED -> "已結束";
                    }, NamedTextColor.GRAY));
        }
    }

    private void info(Player player) {
        Table table = requirePlayingTable(player);
        if (table == null) {
            return;
        }
        RiichiGame game = table.game();
        TileRenderer renderer = plugin.rendererFor(player);
        player.sendMessage(TableView.header(renderer, game));
        player.sendMessage(TableView.seats(renderer, game, table));
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            player.sendMessage(TableView.discards(renderer, game, seat, table));
        }
    }

    /** Switches between drawn tiles and plain text for this player. */
    private void style(Player player, String[] args) {
        if (args.length > 1) {
            TileRenderer.Style style = TileRenderer.Style.parse(args[1]);
            plugin.setStyle(player, style);
        }
        TileRenderer.Style style = plugin.styleFor(player);
        player.sendMessage(info("牌面顯示：" + style.display()
                + "（實際使用：" + (plugin.rendererFor(player).isGraphical() ? "牌圖" : "文字") + "）"));
        if (!plugin.hasResourcePack(player) && !plugin.isResourcePackConfigured()) {
            player.sendMessage(Component.text(
                    "  伺服器尚未設定資源包網址，牌圖無法顯示，請管理員參考 README。",
                    NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("  用法：/mj style auto|tiles|text", NamedTextColor.GRAY));
    }

    /** Moves this table's tiles to where the player is standing. */
    private void placeTable(Player player) {
        Table table = requireTable(player);
        if (table == null) {
            return;
        }
        if (!plugin.isTableSceneEnabled()) {
            player.sendMessage(error("設定裡的 table.enabled 是關的，牌桌不會顯示。"));
            return;
        }
        table.placeAt(player.getLocation());
        player.sendMessage(info("牌桌已放在你站的位置，面向你現在的方向。"));
        player.sendMessage(Component.text(
                "  你的手牌會出現在你這一家的位置，右鍵點牌就是打出去。", NamedTextColor.GRAY));
    }

    /** Puts sample tiles in the world so size and spacing can be judged by eye. */
    private void preview(Player player, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            plugin.preview().clear(player);
            player.sendMessage(info("已清除預覽牌。"));
            return;
        }
        float scale = TilePreview.DEFAULT_SCALE;
        double spacing = TilePreview.DEFAULT_SPACING;
        try {
            if (args.length > 1) {
                scale = Float.parseFloat(args[1]);
            }
            if (args.length > 2) {
                spacing = Double.parseDouble(args[2]);
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(error("用法：/mj preview [大小] [間距]，例如 /mj preview 0.4 0.18"));
            return;
        }
        if (scale <= 0 || scale > 4 || spacing <= 0 || spacing > 2) {
            player.sendMessage(error("大小要在 0 到 4 之間，間距要在 0 到 2 之間。"));
            return;
        }
        plugin.preview().show(player, scale, spacing);
        player.sendMessage(info("測試手牌已放在你面前（大小 " + scale + "、間距 " + spacing + "）。"));
        player.sendMessage(Component.text(
                "  右鍵點牌會回報點到哪一張。想調整就再打一次 /mj preview <大小> <間距>，"
                + "看完用 /mj preview clear 收掉。", NamedTextColor.GRAY));
        if (!plugin.rendererFor(player).isGraphical() && !plugin.hasResourcePack(player)) {
            player.sendMessage(Component.text(
                    "  你還沒載入資源包，會看到白紙而不是牌。", NamedTextColor.RED));
        }
    }

    private void hand(Player player) {
        Table table = requirePlayingTable(player);
        if (table == null) {
            return;
        }
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0) {
            player.sendMessage(error("你不在這局裡。"));
            return;
        }
        List<Action> options = table.game().options(seat);
        TileRenderer renderer = plugin.rendererFor(player);
        player.sendMessage(TableView.hand(renderer, table.game(), seat, options));
        player.sendMessage(TableView.prompt(options));
    }

    // -------------------------------------------------------------- playing

    private void discard(Player player, String[] args, boolean riichi) {
        if (args.length < 2) {
            player.sendMessage(error("用法：/mj " + (riichi ? "riichi" : "discard")
                    + " <牌>，例如 三萬 或 3m"));
            return;
        }
        Tile tile;
        try {
            tile = Tile.parse(args[1]);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(error("看不懂的牌：" + args[1] + "（例如 三萬、五筒、東風，或 3m 5p 1z）"));
            return;
        }
        submitSimple(player, new Action.Discard(tile, riichi));
    }

    private void chi(Player player, String[] args) {
        Table table = requirePlayingTable(player);
        if (table == null) {
            return;
        }
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0 || args.length < 3) {
            player.sendMessage(error("用法：/mj chi <牌1> <牌2>"));
            return;
        }
        Action match = null;
        for (Action action : table.game().options(seat)) {
            if (action instanceof Action.Chi chi
                    && matches(chi, args[1], args[2])) {
                match = action;
                break;
            }
        }
        if (match == null) {
            player.sendMessage(error("現在不能這樣吃。"));
            return;
        }
        submitSimple(player, match);
    }

    private static boolean matches(Action.Chi chi, String first, String second) {
        String left = chi.first().notation();
        String right = chi.second().notation();
        return (left.equalsIgnoreCase(first) && right.equalsIgnoreCase(second))
                || (left.equalsIgnoreCase(second) && right.equalsIgnoreCase(first));
    }

    private void kan(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(error("用法：/mj kan <牌>"));
            return;
        }
        try {
            submitSimple(player, new Action.Kan(Tiles.parse(args[1])));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(error("看不懂的牌：" + args[1]));
        }
    }

    private void submitSimple(Player player, Action action) {
        Table table = requirePlayingTable(player);
        if (table == null) {
            return;
        }
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0) {
            player.sendMessage(error("你不在這局裡。"));
            return;
        }
        if (!table.submit(seat, action)) {
            player.sendMessage(error("現在不能這麼做。"));
        }
    }

    // -------------------------------------------------------------- helpers

    private Table requireTable(Player player) {
        Table table = plugin.tables().tableOf(player);
        if (table == null) {
            player.sendMessage(error("你目前不在任何桌上，用 /mj create 開一桌。"));
        }
        return table;
    }

    private Table requirePlayingTable(Player player) {
        Table table = requireTable(player);
        if (table == null) {
            return null;
        }
        if (table.state() != Table.State.PLAYING || table.game() == null) {
            player.sendMessage(error("這桌還沒開局。"));
            return null;
        }
        return table;
    }

    private void sendHelp(Player player) {
        player.sendMessage(TableView.separator());
        // The version is on the header so a screenshot says which build it came from.
        player.sendMessage(Component.text()
                .append(Component.text("麻將 指令", NamedTextColor.GOLD))
                .append(Component.text("  v" + plugin.getPluginMeta().getVersion(),
                        NamedTextColor.DARK_GRAY))
                .build());
        String[][] lines = {
            {"/mj create [桌名] [japan|taiwan]", "開一張新桌，可選玩法"},
            {"/mj join [桌名]", "加入牌桌"},
            {"/mj bots", "把空位補成電腦玩家"},
            {"/mj start", "四家坐滿後開局"},
            {"/mj leave", "離桌"},
            {"/mj list", "列出所有牌桌"},
            {"/mj info", "看場況與各家牌河"},
            {"/mj hand", "重新顯示自己的手牌"},
            {"/mj discard <牌>", "打牌，例如 /mj discard 三萬"},
            {"/mj riichi <牌>", "立直並打出該牌"},
            {"/mj tsumo | ron", "自摸 / 胡別人打的牌"},
            {"/mj pon | chi <牌> <牌> | kan <牌>", "碰 / 吃 / 槓"},
            {"/mj pass", "跳過鳴牌"},
            {"/mj kyuushu", "九種九牌流局"},
            {"/mj style <auto|tiles|text>", "切換牌圖或文字顯示"},
            {"/mj preview [大小] [間距]", "在世界裡放測試用的立體牌"},
            {"/mj table", "把牌桌放在你站的位置"}
        };
        for (String[] line : lines) {
            player.sendMessage(Component.text()
                    .append(Component.text("  " + line[0], NamedTextColor.YELLOW))
                    .append(Component.text("  " + line[1], NamedTextColor.GRAY))
                    .build());
        }
        player.sendMessage(Component.text("  牌名直接打國字就好：一萬～九萬、一筒～九筒、一索～九索、"
                + "東風 南風 西風 北風 白板 發財 紅中", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  也收短碼：1m-9m 萬子、1p-9p 筒子、1s-9s 索子、"
                + "1z-7z 字牌、0m/0p/0s 赤五", NamedTextColor.DARK_GRAY));
        player.sendMessage(TableView.separator());
    }

    private static Component info(String text) {
        return Component.text("[麻將] ", NamedTextColor.GOLD).append(
                Component.text(text, NamedTextColor.WHITE));
    }

    private static Component error(String text) {
        return Component.text("[麻將] ", NamedTextColor.GOLD).append(
                Component.text(text, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefixed(SUBCOMMANDS, args[0]);
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 3 && sub.equals("create")) {
            return prefixed(List.of("japan", "taiwan"), args[2]);
        }
        if (args.length == 2 && sub.equals("join")) {
            return prefixed(plugin.tables().names(), args[1]);
        }
        if (args.length == 2 && sub.equals("style")) {
            return prefixed(List.of("auto", "tiles", "text"), args[1]);
        }
        if (args.length == 2 && sub.equals("preview")) {
            return prefixed(List.of("clear", "0.4", "0.5", "0.6"), args[1]);
        }
        if (args.length >= 2 && (sub.equals("discard") || sub.equals("riichi")
                || sub.equals("kan") || sub.equals("chi"))) {
            return prefixed(handNotations(player), args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> handNotations(Player player) {
        Table table = plugin.tables().tableOf(player);
        if (table == null || table.state() != Table.State.PLAYING || table.game() == null) {
            return List.of();
        }
        int seat = table.seatOf(player.getUniqueId());
        if (seat < 0) {
            return List.of();
        }
        List<String> notations = new ArrayList<>();
        for (Tile tile : table.game().seat(seat).hand().allConcealed()) {
            String notation = tile.notation();
            if (!notations.contains(notation)) {
                notations.add(notation);
            }
        }
        return notations;
    }

    private static List<String> prefixed(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
