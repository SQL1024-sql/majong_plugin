package io.github.sql1024.mahjong.game;

import io.github.sql1024.mahjong.core.Meld;
import io.github.sql1024.mahjong.core.ShantenCalculator;
import io.github.sql1024.mahjong.core.Tile;
import io.github.sql1024.mahjong.core.Wind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 一個座位上的玩家狀態。 */
public final class Seat {

    private final int index;
    private final UUID id;
    private final String name;
    private boolean bot;

    private int points;
    private Wind wind = Wind.EAST;

    private final List<Tile> hand = new ArrayList<>();
    private Tile drawn;
    private final List<Meld> melds = new ArrayList<>();
    private final List<Discard> discards = new ArrayList<>();

    private boolean riichi;
    private boolean doubleRiichi;
    private boolean ippatsu;
    private boolean riichiFuriten;
    private boolean temporaryFuriten;
    private boolean firstUninterruptedTurn = true;
    private Set<Integer> waits = Set.of();

    public Seat(int index, UUID id, String name, boolean bot, int points) {
        this.index = index;
        this.id = id;
        this.name = name;
        this.bot = bot;
        this.points = points;
    }

    public int index() {
        return index;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isBot() {
        return bot;
    }

    /** 玩家離線時可以改由電腦代打。 */
    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public int points() {
        return points;
    }

    public void addPoints(int delta) {
        this.points += delta;
    }

    public Wind wind() {
        return wind;
    }

    public void setWind(Wind wind) {
        this.wind = wind;
    }

    /** 門前手牌 (不含剛摸到的牌)。 */
    public List<Tile> hand() {
        return hand;
    }

    public Tile drawn() {
        return drawn;
    }

    public void setDrawn(Tile tile) {
        this.drawn = tile;
    }

    /** 門前手牌 + 剛摸到的牌。 */
    public List<Tile> fullHand() {
        List<Tile> all = new ArrayList<>(hand);
        if (drawn != null) {
            all.add(drawn);
        }
        return all;
    }

    public List<Meld> melds() {
        return melds;
    }

    public List<Discard> discards() {
        return discards;
    }

    public boolean riichi() {
        return riichi;
    }

    public boolean doubleRiichi() {
        return doubleRiichi;
    }

    public void declareRiichi(boolean doubleRiichi) {
        this.riichi = true;
        this.doubleRiichi = doubleRiichi;
        this.ippatsu = true;
    }

    public boolean ippatsu() {
        return ippatsu;
    }

    public void clearIppatsu() {
        this.ippatsu = false;
    }

    public boolean firstUninterruptedTurn() {
        return firstUninterruptedTurn;
    }

    public void clearFirstTurn() {
        this.firstUninterruptedTurn = false;
    }

    public boolean isClosed() {
        for (Meld meld : melds) {
            if (meld.type().breaksClosedHand()) {
                return false;
            }
        }
        return true;
    }

    /** 是否有副露 (含暗槓以外的鳴牌)。 */
    public boolean isOpen() {
        return !isClosed();
    }

    public Set<Integer> waits() {
        return waits;
    }

    /** 重新計算聽牌張 (以目前的門前手牌，不含剛摸到的牌)。 */
    public void refreshWaits() {
        int[] counts = Tile.toCounts(hand);
        int tiles = hand.size();
        if (tiles % 3 != 1) {
            this.waits = Set.of();
            return;
        }
        this.waits = new HashSet<>(ShantenCalculator.waits(counts, melds));
    }

    public boolean isTenpai() {
        return !waits.isEmpty();
    }

    /** 振聽判定。 */
    public boolean isFuriten() {
        if (temporaryFuriten || riichiFuriten) {
            return true;
        }
        for (Discard discard : discards) {
            if (waits.contains(discard.tile().id())) {
                return true;
            }
        }
        return false;
    }

    public void setTemporaryFuriten(boolean value) {
        this.temporaryFuriten = value;
    }

    public void setRiichiFuriten() {
        this.riichiFuriten = true;
    }

    public void addTile(Tile tile) {
        hand.add(tile);
        Collections.sort(hand);
    }

    /** 把摸到的牌併入手牌。 */
    public void mergeDrawn() {
        if (drawn != null) {
            hand.add(drawn);
            drawn = null;
            Collections.sort(hand);
        }
    }

    /**
     * 從手牌 (或剛摸到的牌) 移除一張牌。
     *
     * @return 是否成功移除
     */
    public boolean removeTile(Tile tile) {
        if (drawn != null && drawn.equals(tile)) {
            drawn = null;
            return true;
        }
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).equals(tile)) {
                hand.remove(i);
                return true;
            }
        }
        // 找不到完全相同的牌 (例如赤五) 時，退而求其次找同種類的牌
        if (drawn != null && drawn.id() == tile.id()) {
            drawn = null;
            return true;
        }
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).id() == tile.id()) {
                hand.remove(i);
                return true;
            }
        }
        return false;
    }

    public int countInHand(int tileId) {
        int count = 0;
        for (Tile tile : hand) {
            if (tile.id() == tileId) {
                count++;
            }
        }
        if (drawn != null && drawn.id() == tileId) {
            count++;
        }
        return count;
    }

    public void addMeld(Meld meld) {
        melds.add(meld);
    }

    public void resetForRound(Wind wind) {
        this.wind = wind;
        hand.clear();
        melds.clear();
        discards.clear();
        drawn = null;
        riichi = false;
        doubleRiichi = false;
        ippatsu = false;
        riichiFuriten = false;
        temporaryFuriten = false;
        firstUninterruptedTurn = true;
        waits = Set.of();
    }

    @Override
    public String toString() {
        return name + "(" + wind.displayName() + ", " + points + ")";
    }
}
