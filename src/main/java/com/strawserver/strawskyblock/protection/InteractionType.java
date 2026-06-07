package com.strawserver.strawskyblock.protection;

import com.strawserver.strawskyblock.island.IslandFlag;

/**
 * 互動類型，對應到不同的島嶼 Flag。
 */
public enum InteractionType {

    CONTAINER(IslandFlag.VISITOR_CONTAINER),
    BUTTON(IslandFlag.VISITOR_BUTTON),
    REDSTONE(IslandFlag.REDSTONE),
    GENERIC(IslandFlag.VISITOR_BUTTON);

    private final IslandFlag flag;

    InteractionType(IslandFlag flag) {
        this.flag = flag;
    }

    public IslandFlag getFlag() {
        return flag;
    }
}
