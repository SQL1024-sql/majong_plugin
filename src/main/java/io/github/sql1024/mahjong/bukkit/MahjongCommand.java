package io.github.sql1024.mahjong.bukkit;

import io.github.sql1024.mahjong.core.Rules;
import io.github.sql1024.mahjong.core.Tile;
import io.github.sql1024.mahjong.game.CallOption;
import io.github.sql1024.mahjong.game.CallType;
import io.github.sql1024.mahjong.game.MahjongTable;
import io.github.sql1024.mahjong.game.Seat;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@code /mahjong} 指令。 */
public final class MahjongCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "join", "leave", "start", "list", "open", "info",
            "discard", "tsumo", "ron", "riichi", "kan", "pon", "chi", "call", "pass", "kyuushu",
            "help", "reload");

    private final RiichiMahjongPlugin plugin;

    public MahjongCommand(RiichiMahjongPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            help(sender);
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("help")) {
            help(sender);
            return;
        }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("mahjong.admin")) {
                sender.sendMessage(Component.text("你沒有權限。", NamedTextColor.RED));
                return;
            }
            plugin.reloadSettings();
            sender.sendMessage(Component.text("設定已重新載入。", NamedTextColor.GREEN));
            return;
        }
        if (sub.equals("list")) {
            list(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("這個指令只能由玩家使用。", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("mahjong.play")) {
            player.sendMessage(Component.text("你沒有權限打麻將。", NamedTextColor.RED));
            return;
        }

        switch (sub) {
            case "create" -> create(player, args);
            case "join" -> join(player, args);
            case "leave" -> leave(player);
            case "start" -> start(player);
            case "open" -> open(player);
            case "info" -> info(player);
            case "discard" -> discard(player, args);
            case "tsumo" -> turnAction(player, CallType.TSUMO);
            case "kyuushu" -> turnAction(player, CallType.KYUUSHU);
            case "riichi" -> riichi(player, args);
            case "kan" -> kan(player, args);
            case "ron" -> callByType(player, CallType.RON);
            case "pon" -> callByType(player, CallType.PON);
            case "chi" -> chi(player, args);
            case "call" -> call(player, args);
            case "pass" -> pass(player);
            default -> help(player);
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("join")) {
            return List.copyOf(plugin.manager().lobbies().keySet());
        }
        if (sub.equals("create")) {
            return List.of("east", "hanchan", "tonpuusen");
        }
        if ((sub.equals("discard") || sub.equals("kan") || sub.equals("riichi") || sub.equals("chi"))
                && source.getSender() instanceof Player player) {
            MahjongGame game = plugin.manager().gameOf(player.getUniqueId());
            if (game == null) {
                return List.of();
            }
            int seatIndex = game.seatOf(player.getUniqueId());
            List<String> tiles = new ArrayList<>();
            for (Tile tile : game.table().seat(seatIndex).fullHand()) {
                String name = tile.shortName();
                if (!tiles.contains(name)) {
                    tiles.add(name);
                }
            }
            return tiles;
        }
        return List.of();
    }

    // ------------------------------------------------------------------

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("═══ 日本麻將 ═══", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/mj create [east|hanchan] — 開一張新牌桌", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj join <id> — 加入牌桌", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj start — 開始 (空位由電腦補齊)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj open — 打開牌桌畫面", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj discard <牌> — 打牌，例如 /mj discard 5m", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj riichi [牌] — 立直", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj tsumo | ron | pon | chi <牌> <牌> | kan [牌] | pass",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/mj info — 顯示目前牌況", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("牌的寫法：1m=一萬 5p=五筒 9s=九索 1z=東 5z=白 0m=赤五萬",
                NamedTextColor.DARK_GRAY));
    }

    private void list(CommandSender sender) {
        if (plugin.manager().lobbies().isEmpty() && plugin.manager().games().isEmpty()) {
            sender.sendMessage(Component.text("目前沒有牌桌。用 /mj create 開一張。", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("等待中的牌桌：", NamedTextColor.GREEN));
        for (GameManager.Lobby lobby : plugin.manager().lobbies().values()) {
            sender.sendMessage(Component.text("  " + lobby.id() + " (" + lobby.players().size() + "/4) ",
                            NamedTextColor.GRAY)
                    .append(Component.text("[加入]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand("/mj join " + lobby.id()))));
        }
        for (MahjongGame game : plugin.manager().games().values()) {
            sender.sendMessage(Component.text("  " + game.id() + " — 進行中 ("
                    + game.table().roundName() + ")", NamedTextColor.DARK_GRAY));
        }
    }

    private void create(Player player, String[] args) {
        if (plugin.manager().gameOf(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("你已經在對局中了。", NamedTextColor.RED));
            return;
        }
        if (plugin.manager().lobbyOf(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("你已經在一張牌桌裡了。", NamedTextColor.RED));
            return;
        }
        Rules rules = plugin.defaultRules();
        if (args.length > 1) {
            String mode = args[1].toLowerCase();
            if (mode.startsWith("east") || mode.startsWith("ton")) {
                rules = rules.withTargetRounds(1);
            } else if (mode.startsWith("han")) {
                rules = rules.withTargetRounds(2);
            }
        }
        GameManager.Lobby lobby = plugin.manager().create(player, rules);
        player.sendMessage(Component.text("已開好牌桌 " + lobby.id()
                + "，其他人可用 /mj join " + lobby.id() + " 加入。", NamedTextColor.GREEN));
        player.sendMessage(Component.text("人到齊或想直接開始就用 ", NamedTextColor.GRAY)
                .append(Component.text("[/mj start]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/mj start"))));
    }

    private void join(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法：/mj join <id>", NamedTextColor.RED));
            return;
        }
        GameManager.Lobby lobby = plugin.manager().lobbies().get(args[1].toLowerCase());
        if (lobby == null) {
            player.sendMessage(Component.text("找不到這張牌桌。", NamedTextColor.RED));
            return;
        }
        if (!plugin.manager().join(lobby, player)) {
            player.sendMessage(Component.text("加入失敗 (人滿了或你已經在裡面)。", NamedTextColor.RED));
        }
    }

    private void leave(Player player) {
        GameManager.Lobby lobby = plugin.manager().lobbyOf(player.getUniqueId());
        if (lobby != null) {
            plugin.manager().leave(lobby, player);
            player.sendMessage(Component.text("已離開牌桌。", NamedTextColor.YELLOW));
            return;
        }
        MahjongGame game = plugin.manager().gameOf(player.getUniqueId());
        if (game != null) {
            game.handleQuit(player.getUniqueId());
            player.sendMessage(Component.text("已交給電腦代打。", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("你不在任何牌桌裡。", NamedTextColor.RED));
    }

    private void start(Player player) {
        GameManager.Lobby lobby = plugin.manager().lobbyOf(player.getUniqueId());
        if (lobby == null) {
            player.sendMessage(Component.text("你不在任何等待中的牌桌裡。", NamedTextColor.RED));
            return;
        }
        if (!lobby.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("只有開桌的人可以開始。", NamedTextColor.RED));
            return;
        }
        plugin.manager().start(lobby);
    }

    private void open(Player player) {
        MahjongGame game = plugin.manager().gameOf(player.getUniqueId());
        if (game == null) {
            player.sendMessage(Component.text("你不在對局中。", NamedTextColor.RED));
            return;
        }
        game.openGui(player);
    }

    private void info(Player player) {
        MahjongGame game = plugin.manager().gameOf(player.getUniqueId());
        if (game == null) {
            player.sendMessage(Component.text("你不在對局中。", NamedTextColor.RED));
            return;
        }
        MahjongTable table = game.table();
        int seatIndex = game.seatOf(player.getUniqueId());
        Seat seat = table.seat(seatIndex);
        player.sendMessage(Component.text("── " + table.roundName() + " ──", NamedTextColor.GREEN));
        player.sendMessage(Component.text("剩餘 " + table.wall().remaining() + " 張，立直棒 "
                + table.riichiSticks() + " 本", NamedTextColor.GRAY));
        player.sendMessage(Component.text("寶牌指示牌：", NamedTextColor.GRAY)
                .append(TileRender.text(table.wall().doraIndicators())));
        player.sendMessage(Component.text("手牌：", NamedTextColor.GRAY)
                .append(TileRender.text(seat.hand())));
        if (seat.drawn() != null) {
            player.sendMessage(Component.text("摸到：", NamedTextColor.GRAY)
                    .append(TileRender.text(seat.drawn())));
        }
        if (!seat.melds().isEmpty()) {
            Component melds = Component.text("副露：", NamedTextColor.GRAY);
            for (var meld : seat.melds()) {
                melds = melds.append(Component.text(" ")).append(TileRender.text(meld));
            }
            player.sendMessage(melds);
        }
        if (seat.isTenpai()) {
            List<Tile> waits = new ArrayList<>();
            for (int id : seat.waits()) {
                waits.add(Tile.of(id));
            }
            player.sendMessage(Component.text("聽牌：", NamedTextColor.AQUA).append(TileRender.text(waits))
                    .append(seat.isFuriten()
                            ? Component.text(" (振聽)", NamedTextColor.RED) : Component.empty()));
        }
        for (Seat other : table.seats()) {
            player.sendMessage(Component.text("  " + other.wind().displayName() + " " + other.name()
                    + " — " + other.points() + " 點" + (other.riichi() ? " [立直]" : ""),
                    other.index() == seatIndex ? NamedTextColor.YELLOW : NamedTextColor.WHITE));
        }
    }

    private record Context(MahjongGame game, int seat) {
    }

    private Context context(Player player) {
        MahjongGame game = plugin.manager().gameOf(player.getUniqueId());
        if (game == null) {
            player.sendMessage(Component.text("你不在對局中。", NamedTextColor.RED));
            return null;
        }
        return new Context(game, game.seatOf(player.getUniqueId()));
    }

    private void discard(Player player, String[] args) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("用法：/mj discard <牌>，例如 /mj discard 3p",
                    NamedTextColor.RED));
            return;
        }
        Tile tile = parseTile(player, args[1]);
        if (tile == null) {
            return;
        }
        if (!context.game().table().discard(context.seat(), tile, false)) {
            player.sendMessage(Component.text("現在不能打這張牌。", NamedTextColor.RED));
        }
    }

    private void riichi(Player player, String[] args) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        MahjongTable table = context.game().table();
        if (table.phase() != MahjongTable.Phase.TURN || table.currentSeat() != context.seat()
                || !table.currentOptions().canRiichi()) {
            player.sendMessage(Component.text("現在不能立直。", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2) {
            Tile tile = parseTile(player, args[1]);
            if (tile == null) {
                return;
            }
            if (!table.discard(context.seat(), tile, true)) {
                player.sendMessage(Component.text("立直不能打這張牌。", NamedTextColor.RED));
            }
            return;
        }
        Component line = Component.text("立直！選擇要打出的牌：", NamedTextColor.GOLD);
        for (Tile tile : table.currentOptions().riichiDiscards()) {
            line = line.append(Component.text(" "))
                    .append(Component.text("[" + tile.displayName() + "]", TileRender.color(tile))
                            .clickEvent(ClickEvent.runCommand("/mj riichi " + tile.shortName())));
        }
        player.sendMessage(line);
    }

    private void kan(Player player, String[] args) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        Tile tile = null;
        if (args.length >= 2) {
            tile = parseTile(player, args[1]);
            if (tile == null) {
                return;
            }
        } else {
            List<CallOption> options = context.game().table().currentOptions().kanOptions();
            if (!options.isEmpty()) {
                tile = options.getFirst().target();
            }
        }
        if (tile == null || !context.game().kan(context.seat(), tile)) {
            player.sendMessage(Component.text("現在不能槓。", NamedTextColor.RED));
        }
    }

    private void turnAction(Player player, CallType type) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        if (!context.game().table().declareTurnAction(context.seat(), CallOption.of(type))) {
            player.sendMessage(Component.text("現在不能" + type.displayName() + "。", NamedTextColor.RED));
        }
    }

    private void callByType(Player player, CallType type) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        List<CallOption> options = context.game().table().pendingOptions(context.seat());
        for (CallOption option : options) {
            if (option.type() == type) {
                context.game().table().declareCall(context.seat(), option);
                return;
            }
        }
        player.sendMessage(Component.text("現在不能" + type.displayName() + "。", NamedTextColor.RED));
    }

    private void chi(Player player, String[] args) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        List<CallOption> options = context.game().table().pendingOptions(context.seat());
        List<CallOption> chiOptions = new ArrayList<>();
        for (CallOption option : options) {
            if (option.type() == CallType.CHI) {
                chiOptions.add(option);
            }
        }
        if (chiOptions.isEmpty()) {
            player.sendMessage(Component.text("現在不能吃。", NamedTextColor.RED));
            return;
        }
        if (args.length >= 3) {
            Tile first = parseTile(player, args[1]);
            Tile second = parseTile(player, args[2]);
            if (first == null || second == null) {
                return;
            }
            for (CallOption option : chiOptions) {
                List<Integer> ids = option.handTiles().stream().map(Tile::id).sorted().toList();
                List<Integer> wanted = List.of(Math.min(first.id(), second.id()),
                        Math.max(first.id(), second.id()));
                if (ids.equals(wanted)) {
                    context.game().table().declareCall(context.seat(), option);
                    return;
                }
            }
            player.sendMessage(Component.text("沒有這個吃的組合。", NamedTextColor.RED));
            return;
        }
        if (chiOptions.size() == 1) {
            context.game().table().declareCall(context.seat(), chiOptions.getFirst());
            return;
        }
        Component line = Component.text("選擇要吃的組合：", NamedTextColor.AQUA);
        for (CallOption option : chiOptions) {
            String arguments = option.handTiles().getFirst().shortName() + " "
                    + option.handTiles().get(1).shortName();
            line = line.append(Component.text(" "))
                    .append(Component.text("[" + option.describe() + "]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand("/mj chi " + arguments)));
        }
        player.sendMessage(line);
    }

    private void call(Player player, String[] args) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("用法：/mj call <編號>", NamedTextColor.RED));
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("編號必須是數字。", NamedTextColor.RED));
            return;
        }
        if (!context.game().chooseCall(context.seat(), index)) {
            player.sendMessage(Component.text("這個選項現在不能用。", NamedTextColor.RED));
        }
    }

    private void pass(Player player) {
        Context context = context(player);
        if (context == null) {
            return;
        }
        if (!context.game().pass(context.seat())) {
            player.sendMessage(Component.text("現在沒有要決定的鳴牌。", NamedTextColor.RED));
        }
    }

    private Tile parseTile(Player player, String text) {
        try {
            return Tile.parse(text);
        } catch (RuntimeException exception) {
            player.sendMessage(Component.text("看不懂的牌：" + text + " (例如 1m 5p 9s 1z 0m)",
                    NamedTextColor.RED));
            return null;
        }
    }
}
