package com.strawserver.strawskyblock.island;

import java.util.Locale;

/**
 * 島嶼設定旗標。fallbackDefault 為內建預設，實際預設可被 config 覆寫。
 */
public enum IslandFlag {

    VISITOR_BREAK("visitor-break", "訪客破壞方塊", false),
    VISITOR_PLACE("visitor-place", "訪客放置方塊", false),
    VISITOR_CONTAINER("visitor-container", "訪客開啟容器", false),
    VISITOR_BUTTON("visitor-button", "訪客使用按鈕/門/拉桿", false),
    VISITOR_PICKUP("visitor-pickup", "訪客撿取物品", false),
    VISITOR_DAMAGE_ANIMALS("visitor-damage-animals", "訪客攻擊動物", false),
    PVP("pvp", "玩家對戰 PvP", false),
    EXPLOSION("explosion", "爆炸破壞", false),
    FIRE_SPREAD("fire-spread", "火焰蔓延", false),
    MOB_GRIEFING("mob-griefing", "生物破壞", false),
    REDSTONE("redstone", "紅石互動", true);

    private final String key;
    private final String displayName;
    private final boolean fallbackDefault;

    IslandFlag(String key, String displayName, boolean fallbackDefault) {
        this.key = key;
        this.displayName = displayName;
        this.fallbackDefault = fallbackDefault;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean getFallbackDefault() {
        return fallbackDefault;
    }

    public static IslandFlag fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (IslandFlag flag : values()) {
            if (flag.key.equalsIgnoreCase(key)) {
                return flag;
            }
        }
        try {
            return IslandFlag.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
