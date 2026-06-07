package com.strawserver.strawskyblock.island;

import java.util.Locale;

/**
 * 島嶼角色，數值越大權限越高。
 */
public enum IslandRole {

    VISITOR(0, "訪客"),
    MEMBER(1, "成員"),
    ADMIN(2, "副島主"),
    OWNER(3, "島主");

    private final int weight;
    private final String displayName;

    IslandRole(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean atLeast(IslandRole other) {
        return this.weight >= other.weight;
    }

    public boolean isTrusted() {
        return this.weight >= MEMBER.weight;
    }

    public static IslandRole fromString(String value) {
        if (value == null) {
            return VISITOR;
        }
        try {
            return IslandRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VISITOR;
        }
    }
}
