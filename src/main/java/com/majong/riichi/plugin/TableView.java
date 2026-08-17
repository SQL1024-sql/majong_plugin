package com.majong.riichi.plugin;

import com.majong.riichi.core.Hand;
import com.majong.riichi.core.Meld;
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

/**
 * Turns the state of a table into chat messages. Every method takes the
 * renderer to draw tiles with, because two players at the same table may be
 * seeing different things depending on whether they loaded the resource pack.
 */
public final class TableView {

    private static final Component SEPARATOR =
            Component.text("─".repeat(40), NamedTextColor.DARK_GRAY);

    private TableView() {
    }

    public static Component separator() {
        return SEPARATOR;
    }

    private static Component tiles(TileRenderer renderer, List<Tile> tiles) {
        TextComponent.Builder builder = Component.text();
        for (Tile tile : tiles) {
            builder.append(Component.text(" "));
            builder.append(renderer.render(tile));
        }
        return builder.build();
    }

    /** The header line: round, honba, sticks and how much wall is left. */
    public static Component header(TileRenderer renderer, RiichiGame game) {
        return Component.text()
                .append(Component.text(game.roundName(), NamedTextColor.YELLOW))
                .append(Component.text("  剩餘", NamedTextColor.GRAY))
                .append(Component.text(game.wallRemaining(), NamedTextColor.WHITE))
                .append(Component.text("張  供託", NamedTextColor.GRAY))
                .append(Component.text(game.riichiSticks(), NamedTextColor.WHITE))
                .append(Component.text("  寶牌", NamedTextColor.GRAY))
                .append(tiles(renderer, game.doraIndicators()))
                .build();
    }

    /** One line per seat: wind, name, score and the tiles they have shown. */
    public static Component seats(TileRenderer renderer, RiichiGame game, Table table) {
        TextComponent.Builder builder = Component.text();
        for (int seat = 0; seat < RiichiGame.SEATS; seat++) {
            SeatState state = game.seat(seat);
            boolean turn = game.phase() == RiichiGame.Phase.ACT && game.currentSeat() == seat;
            builder.append(Component.newline());
            builder.append(Component.text(turn ? "▶ " : "  ",
                    turn ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
            builder.append(Component.text(Tiles.windLetter(game.seatWind(seat)),
                    NamedTextColor.GOLD));
            builder.append(Component.text(" " + table.displayName(seat), NamedTextColor.WHITE));
            builder.append(Component.text(" " + state.score(), NamedTextColor.GRAY));
            if (state.riichi()) {
                builder.append(Component.text(" 立直", NamedTextColor.RED));
            }
            for (Meld meld : state.hand().melds()) {
                builder.append(meldComponent(renderer, meld));
            }
        }
        return builder.build();
    }

    /** A called group, with a closed kan wrapped in brackets. */
    public static Component meldComponent(TileRenderer renderer, Meld meld) {
        TextComponent.Builder builder = Component.text().append(Component.text("  "));
        boolean concealed = meld.type() == com.majong.riichi.core.MeldType.ANKAN;
        if (concealed) {
            builder.append(Component.text("[", NamedTextColor.DARK_GRAY));
        }
        builder.append(tiles(renderer, meld.tiles()));
        if (concealed) {
            builder.append(Component.text(" ]", NamedTextColor.DARK_GRAY));
        }
        return builder.build();
    }

    /** A player's own pond, most recent tile last. */
    public static Component discards(TileRenderer renderer, RiichiGame game, int seat, Table table) {
        return Component.text()
                .append(Component.text(table.displayName(seat) + "的牌河", NamedTextColor.GRAY))
                .append(tiles(renderer, game.seat(seat).discards()))
                .build();
    }

    /**
     * The player's own tiles, each one clickable to throw it. Tiles that cannot
     * legally be discarded are dimmed rather than hidden, so the player still
     * sees their whole hand.
     */
    public static Component hand(TileRenderer renderer, RiichiGame game, int seat,
                                 List<Action> options) {
        Hand hand = game.seat(seat).hand();
        TextComponent.Builder builder = Component.text()
                .append(Component.text("手牌", NamedTextColor.GRAY));
        for (Tile tile : hand.concealed()) {
            builder.append(Component.text(" "));
            builder.append(discardable(renderer, tile, options));
        }
        if (hand.drawn() != null) {
            builder.append(Component.text("  ┃", NamedTextColor.DARK_GRAY));
            builder.append(Component.text(" "));
            builder.append(discardable(renderer, hand.drawn(), options));
        }
        for (Meld meld : hand.melds()) {
            builder.append(meldComponent(renderer, meld));
        }
        return builder.build();
    }

    private static Component discardable(TileRenderer renderer, Tile tile, List<Action> options) {
        Component text = renderer.render(tile);
        boolean legal = options.stream().anyMatch(action ->
                action instanceof Action.Discard discard
                        && discard.tile().equals(tile) && !discard.riichi());
        if (!legal) {
            return renderer.isGraphical() ? text : text.color(NamedTextColor.DARK_GRAY);
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
        return Component.text().append(Component.text("選擇", NamedTextColor.GRAY))
                .append(builder.build()).build();
    }

    private static Component button(Action action) {
        return switch (action) {
            case Action.Tsumo ignored -> button("自摸", "/mj tsumo", NamedTextColor.GOLD);
            case Action.Ron ignored -> button("胡牌", "/mj ron", NamedTextColor.GOLD);
            case Action.Pon ignored -> button("碰", "/mj pon", NamedTextColor.AQUA);
            case Action.Kan kan -> button("槓 " + Tiles.display(kan.kind()),
                    "/mj kan " + Tiles.notation(kan.kind()), NamedTextColor.AQUA);
            case Action.Chi chi -> button(
                    "吃 " + Tiles.display(chi.first().kind()) + Tiles.display(chi.second().kind()),
                    "/mj chi " + chi.first().notation() + " " + chi.second().notation(),
                    NamedTextColor.AQUA);
            case Action.Pass ignored -> button("跳過", "/mj pass", NamedTextColor.GRAY);
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
    public static Component result(TileRenderer renderer, RiichiGame game, Table table,
                                   HandResult result) {
        TextComponent.Builder builder = Component.text().append(SEPARATOR).append(Component.newline());
        switch (result) {
            case HandResult.Won won -> {
                builder.append(Component.text(table.displayName(won.winner()), NamedTextColor.GOLD));
                builder.append(Component.text(won.isTsumo() ? " 自摸" : " 胡牌",
                        NamedTextColor.YELLOW));
                if (!won.isTsumo()) {
                    builder.append(Component.text(" ← " + table.displayName(won.loser()),
                            NamedTextColor.GRAY));
                }
                builder.append(Component.newline());
                builder.append(Component.text("  "));
                builder.append(tiles(renderer, game.seat(won.winner()).hand().allConcealed()));
                builder.append(Component.newline());
                for (String line : won.lines()) {
                    builder.append(Component.text("  " + line, NamedTextColor.WHITE));
                    builder.append(Component.newline());
                }
                builder.append(Component.text("  " + won.summary(), NamedTextColor.YELLOW));
            }
            case HandResult.ExhaustiveDraw draw -> {
                builder.append(Component.text("流局", NamedTextColor.AQUA));
                builder.append(Component.newline());
                StringBuilder ready = new StringBuilder();
                for (int seat : draw.tenpaiSeats()) {
                    ready.append(table.displayName(seat)).append(' ');
                }
                builder.append(Component.text("  聽牌 " + (ready.isEmpty() ? "無" : ready),
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
