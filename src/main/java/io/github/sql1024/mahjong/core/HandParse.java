package io.github.sql1024.mahjong.core;

import java.util.List;

/**
 * 一種和牌的拆解方式。
 *
 * @param form   牌型
 * @param groups 全部的面子與雀頭 (含副露)
 */
public record HandParse(Form form, List<Group> groups) {

    public enum Form {
        /** 四面子一雀頭 */
        STANDARD,
        /** 七對子 */
        SEVEN_PAIRS,
        /** 國士無雙 */
        THIRTEEN_ORPHANS
    }

    public HandParse {
        groups = List.copyOf(groups);
    }

    public Group pair() {
        for (Group group : groups) {
            if (group.isPair()) {
                return group;
            }
        }
        return null;
    }

    public List<Group> melds() {
        return groups.stream().filter(g -> !g.isPair()).toList();
    }

    @Override
    public String toString() {
        return form + groups.toString();
    }
}
