package com.majong.riichi.plugin;

import com.majong.riichi.core.Hand;
import com.majong.riichi.core.HandValue;
import com.majong.riichi.core.Meld;
import com.majong.riichi.core.ScoredYaku;
import com.majong.riichi.core.Tile;
import com.majong.riichi.core.Tiles;
import com.majong.riichi.game.Action;
import com.majong.riichi.game.HandResult;
import com.majong.riichi.game.RiichiGame;
import com.majong.riichi.game.SeatState;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Turns the state of a table into chat messages. */
public final class TableView {

    private static final Component SEPARATOR =
            Component.text("─".repeat(40), NamedTextColor.DARK_GRAY);

    private TableView() {
    }

    public static Component separator() {
        return SEPARATOR;
    }

    public static Component tile(Tile tile) {
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

    private static Component tiles(List<Tile> tiles) {
        TextComponent.Builder builder = Component.text();
        for (Tile tile : tiles) {
            builder.append(Component.text(" "));
            builder.append(tile(tile));
        }
        return builder.build();
    }

    /** The header line: round, honba, sticks and how much wall is left. */
    public static Component header(RiichiGame game) {
        return Component.text()
                .append(Component.text(game.roundName(), NamedTextColor.YELLOW))
                .append(Component.text("  残り", NamedTextColor.GRAY))
                .append(Component.text(game.wallRemaining(), NamedTextColor.WHITE))
                .append(Component.text("枚  供託", NamedTextColor.GRAY))
                .append(Component.text(game.riichiSticks(), NamedTextColor.WHITE))
                .append(Component.text("  ドラ", NamedTextColor.GRAY))
                .append(tiles(game.doraIndicators()))
                .build();
    }

    /** One line per seat: wind, name, score and the tiles they have shown. */
    public static Component seats(RiichiGame game, Table table) {
        TextComponent.Builder builder = Component.text();
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            SeatState state = game.seat(seat);
            boolean turn = game.phase() == RiichiGame.Phase.ACT && game.currentSeat() == seat;
            builder.append(Component.newline());
            builder.append(Component.text(turn ? "▶ " : "  ",
                    turn ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
            builder.append(Component.text(Tiles.display(game.seatWind(seat)), NamedTextColor.GOLD));
            builder.append(Component.text(" " + table.displayName(seat), NamedTextColor.WHITE));
            builder.append(Component.text(" " + state.score(), NamedTextColor.GRAY));
            if (state.riichi()) {
                builder.append(Component.text(" 立直", NamedTextColor.RED));
            }
            for (Meld meld : state.hand().melds()) {
                builder.append(Component.text(" ["));
                builder.append(tiles(meld.tiles()));
                builder.append(Component.text(" ]"));
            }
        }
        return builder.build();
    }

    /** A player's own pond, most recent tile last. */
    public static Component discards(RiichiGame game, int seat, Table table) {
        return Component.text()
                .append(Component.text(table.displayName(seat) + "の河", NamedTextColor.GRAY))
                .append(tiles(game.seat(seat).discards()))
                .build();
    }

    /**
     * The player's own tiles, each one clickable to throw it. Tiles that would
     * break a riichi hand are still shown so the player can see the whole hand.
     */
    public static Component hand(RiichiGame game, int seat, List<Action> options) {
        Hand hand = game.seat(seat).hand();
        TextComponent.Builder builder = Component.text()
                .append(Component.text("手牌", NamedTextColor.GRAY));
        for (Tile tile : hand.concealed()) {
            builder.append(Component.text(" "));
            builder.append(discardable(tile, options));
        }
        if (hand.drawn() != null) {
            builder.append(Component.text("  ┃", NamedTextColor.DARK_GRAY));
            builder.append(Component.text(" "));
            builder.append(discardable(hand.drawn(), options));
        }
        for (Meld meld : hand.melds()) {
            builder.append(Component.text("  ["));
            builder.append(tiles(meld.tiles()));
            builder.append(Component.text(" ]"));
        }
        return builder.build();
    }

    private static Component discardable(Tile tile, List<Action> options) {
        boolean legal = options.stream().anyMatch(action ->
                action instanceof Action.Discard discard
                        && discard.tile().equals(tile) && !discard.riichi());
        Component text = tile(tile);
        if (!legal) {
            return text.color(NamedTextColor.DARK_GRAY);
        }
        return text.clickEvent(ClickEvent.runCommand("/mj discard " + tile.notation()))
                .hoverEvent(HoverEvent.showText(
                        Component.text("打 " + Tiles.display(tile.kind()))));
    }

    /** The buttons for whatever the player may do right now. */
    public static Component prompt(List<Action> options) {
        TextComponent.Builder builder = Component.text();
        boolean any = false;
        for (Action action : options) {
            Component button = button(action);
            if (button == null) {
                continue;
            }
            builder.append(Component.text(" "));
            builder.append(button);
            any = true;
        }
        if (!any) {
            return Component.empty();
        }
        return Component.text().append(Component.text("選択", NamedTextColor.GRAY))
                .append(builder.build()).build();
    }

    private static Component button(Action action) {
        return switch (action) {
            case Action.Tsumo ignored -> button("ツモ", "/mj tsumo", NamedTextColor.GOLD);
            case Action.Ron ignored -> button("ロン", "/mj ron", NamedTextColor.GOLD);
            case Action.Pon ignored -> button("ポン", "/mj pon", NamedTextColor.AQUA);
            case Action.Kan kan -> button("カン " + Tiles.display(kan.kind()),
                    "/mj kan " + Tiles.notation(kan.kind()), NamedTextColor.AQUA);
            case Action.Chi chi -> button(
                    "チー " + Tiles.display(chi.first().kind()) + Tiles.display(chi.second().kind()),
                    "/mj chi " + chi.first().notation() + " " + chi.second().notation(),
                    NamedTextColor.AQUA);
            case Action.Pass ignored -> button("スルー", "/mj pass", NamedTextColor.GRAY);
            case Action.NineTerminals ignored ->
                    button("九種九牌", "/mj kyuushu", NamedTextColor.LIGHT_PURPLE);
            case Action.Discard discard -> discard.riichi()
                    ? button("立直 " + Tiles.display(discard.tile().kind()),
                            "/mj riichi " + discard.tile().notation(), NamedTextColor.RED)
                    : null;
        };
    }

    private static Component button(String label, String command, NamedTextColor colour) {
        return Component.text("[" + label + "]", colour)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    /** How a hand ended, ready to broadcast to the table. */
    public static Component result(RiichiGame game, Table table, HandResult result) {
        TextComponent.Builder builder = Component.text().append(SEPARATOR).append(Component.newline());
        switch (result) {
            case HandResult.Won won -> {
                HandValue value = won.value();
                builder.append(Component.text(table.displayName(won.winner()), NamedTextColor.GOLD));
                builder.append(Component.text(won.isTsumo() ? " ツモ和了" : " ロン和了",
                        NamedTextColor.YELLOW));
                if (!won.isTsumo()) {
                    builder.append(Component.text(" ← " + table.displayName(won.loser()),
                            NamedTextColor.GRAY));
                }
                builder.append(Component.newline());
                for (ScoredYaku yaku : value.yaku()) {
                    builder.append(Component.text("  " + yaku.display(), NamedTextColor.WHITE));
                    builder.append(Component.newline());
                }
                if (value.dora() + value.uraDora() + value.redDora() > 0) {
                    builder.append(Component.text("  ドラ" + value.dora()
                            + " 裏ドラ" + value.uraDora() + " 赤" + value.redDora(),
                            NamedTextColor.WHITE));
                    builder.append(Component.newline());
                }
                builder.append(Component.text("  " + value.summary(), NamedTextColor.YELLOW));
            }
            case HandResult.ExhaustiveDraw draw -> {
                builder.append(Component.text("荒牌平局", NamedTextColor.AQUA));
                builder.append(Component.newline());
                StringBuilder ready = new StringBuilder();
                for (int seat : draw.tenpaiSeats()) {
                    ready.append(table.displayName(seat)).append(' ');
                }
                builder.append(Component.text("  聴牌 " + (ready.isEmpty() ? "なし" : ready),
                        NamedTextColor.WHITE));
            }
            case HandResult.AbortiveDraw abortive -> builder.append(
                    Component.text("途中流局 " + abortive.reason().display(), NamedTextColor.AQUA));
        }
        builder.append(Component.newline());
        int[] deltas = result.deltas();
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            if (deltas[seat] == 0) {
                continue;
            }
            builder.append(Component.text("  " + table.displayName(seat) + " ", NamedTextColor.GRAY));
            builder.append(Component.text((deltas[seat] > 0 ? "+" : "") + deltas[seat],
                    deltas[seat] > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            builder.append(Component.text(" → " + game.seat(seat).score(), NamedTextColor.DARK_GRAY));
            builder.append(Component.newline());
        }
        return builder.append(SEPARATOR).build();
    }

    /** The final table order once the game is over. */
    public static Component standings(RiichiGame game, Table table) {
        TextComponent.Builder builder = Component.text()
                .append(Component.text("終局", NamedTextColor.GOLD));
        int place = 1;
        for (SeatState state : game.standings()) {
            builder.append(Component.newline());
            builder.append(Component.text("  " + place + "位 ", NamedTextColor.YELLOW));
            builder.append(Component.text(table.displayName(state.seat()), NamedTextColor.WHITE));
            builder.append(Component.text(" " + state.score(), NamedTextColor.GRAY));
            place++;
        }
        return builder.build();
    }
}
