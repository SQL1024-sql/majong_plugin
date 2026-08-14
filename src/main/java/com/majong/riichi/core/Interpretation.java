package com.majong.riichi.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One reading of a finished hand: which groups it is made of and which of them
 * the winning tile completed. A hand such as {@code 111222333m} can be read
 * several ways, and each reading may score differently.
 */
public record Interpretation(List<Group> groups, int winningGroup, WaitType waitType,
                             boolean sevenPairs, boolean thirteenOrphans) {

    public Interpretation {
        groups = List.copyOf(groups);
    }

    /** Every ordinary reading of a hand, one per group the winning tile could belong to. */
    public static List<Interpretation> standard(int[] concealedCounts, List<Meld> melds, int winningKind,
                                                boolean ron) {
        List<Interpretation> interpretations = new ArrayList<>();
        List<List<Group>> decompositions =
                WinChecker.standardDecompositions(concealedCounts, melds.size());
        for (List<Group> concealedGroups : decompositions) {
            for (int i = 0; i < concealedGroups.size(); i++) {
                Group group = concealedGroups.get(i);
                if (!group.contains(winningKind)) {
                    continue;
                }
                List<Group> groups = new ArrayList<>(concealedGroups);
                // A triplet completed by an opponent's discard counts as an open one.
                if (ron && group.type() == GroupType.TRIPLET) {
                    groups.set(i, Group.triplet(group.kind(), false));
                }
                for (Meld meld : melds) {
                    groups.add(Group.fromMeld(meld));
                }
                interpretations.add(new Interpretation(groups, i, waitOf(group, winningKind), false, false));
            }
        }
        return interpretations;
    }

    public static Interpretation sevenPairs(int[] counts, int winningKind) {
        List<Group> groups = new ArrayList<>(7);
        int winningIndex = -1;
        for (int kind = 0; kind < Tiles.KINDS; kind++) {
            if (counts[kind] == 2) {
                if (kind == winningKind) {
                    winningIndex = groups.size();
                }
                groups.add(Group.pair(kind));
            }
        }
        return new Interpretation(groups, winningIndex, WaitType.TANKI, true, false);
    }

    public static Interpretation forThirteenOrphans() {
        List<Group> groups = new ArrayList<>(13);
        for (int kind : WinChecker.ORPHANS) {
            groups.add(Group.pair(kind));
        }
        return new Interpretation(groups, -1, WaitType.TANKI, false, true);
    }

    private static WaitType waitOf(Group group, int winningKind) {
        return switch (group.type()) {
            case PAIR -> WaitType.TANKI;
            case TRIPLET, KAN -> WaitType.SHANPON;
            case RUN -> {
                if (winningKind == group.kind() + 1) {
                    yield WaitType.KANCHAN;
                }
                boolean lowEdge = Tiles.rankOf(group.kind()) == 1 && winningKind == group.kind() + 2;
                boolean highEdge = Tiles.rankOf(group.kind()) == 7 && winningKind == group.kind();
                yield lowEdge || highEdge ? WaitType.PENCHAN : WaitType.RYANMEN;
            }
        };
    }

    public List<Group> sets() {
        return groups.stream().filter(Group::isSet).toList();
    }

    public Group pair() {
        return groups.stream().filter(group -> group.type() == GroupType.PAIR).findFirst().orElse(null);
    }

    public boolean isSpecial() {
        return sevenPairs || thirteenOrphans;
    }
}
