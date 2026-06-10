package com.strawserver.strawskyblock.island;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 空島佔用狀態（純邏輯，不依賴 Bukkit）。v1.0.39
 *
 * <p>用於「離開空島時馬上暫停空島」：以島嶼 UUID 為單位，記錄目前「身處該島範圍內」的線上玩家集合。
 * 當某島玩家集合為空時即視為暫停（無人佔用）；只要有任一玩家在場即為啟用。</p>
 *
 * <p>設計重點：</p>
 * <ul>
 *   <li>每位玩家只會被計入「目前所在的一座島」（或不在任何島）。移動／傳送／換世界時更新其所屬島，
 *       會自動從舊島移除、加入新島，因此多人情境下「只剩最後一人離開」時才會暫停。</li>
 *   <li>玩家不在任何島（{@code null}）時，從先前所在島移除。</li>
 *   <li>不持有任何玩家或島嶼的業務資料，僅維護「在場」對應關係，可安全於單元測試中使用。</li>
 * </ul>
 */
public final class IslandOccupancyState {

    /** 島嶼 UUID → 目前在該島範圍內的玩家集合。 */
    private final Map<UUID, Set<UUID>> playersByIsland = new HashMap<>();
    /** 玩家 UUID → 目前被計入的島嶼 UUID（用於移動時從舊島移除）。 */
    private final Map<UUID, UUID> islandByPlayer = new HashMap<>();

    /**
     * 設定某玩家目前所在的島嶼。
     *
     * @param playerUuid 玩家 UUID
     * @param islandUuid 玩家目前所在島嶼 UUID；{@code null} 表示不在任何島（例如離開到主世界）
     * @return 是否有任何島的「啟用 / 暫停」狀態因此改變（由有人變無人，或由無人變有人）
     */
    public boolean setPlayerIsland(UUID playerUuid, UUID islandUuid) {
        UUID previous = islandByPlayer.get(playerUuid);
        if (Objects.equals(previous, islandUuid)) {
            return false;
        }
        boolean changed = false;

        if (previous != null) {
            Set<UUID> set = playersByIsland.get(previous);
            if (set != null) {
                set.remove(playerUuid);
                if (set.isEmpty()) {
                    playersByIsland.remove(previous);
                    changed = true; // 舊島最後一人離開 → 轉為暫停
                }
            }
        }

        if (islandUuid != null) {
            Set<UUID> set = playersByIsland.computeIfAbsent(islandUuid, k -> new HashSet<>());
            boolean wasEmpty = set.isEmpty();
            set.add(playerUuid);
            if (wasEmpty) {
                changed = true; // 新島由無人 → 有人，轉為啟用
            }
            islandByPlayer.put(playerUuid, islandUuid);
        } else {
            islandByPlayer.remove(playerUuid);
        }

        return changed;
    }

    /**
     * 玩家離線 / 被踢出 / 完全離開所有島：從目前所在島移除。
     *
     * @return 是否使某島由有人轉為暫停
     */
    public boolean removePlayer(UUID playerUuid) {
        return setPlayerIsland(playerUuid, null);
    }

    /**
     * 該島是否啟用（至少有一位玩家在場）。
     */
    public boolean isActive(UUID islandUuid) {
        if (islandUuid == null) {
            return false;
        }
        Set<UUID> set = playersByIsland.get(islandUuid);
        return set != null && !set.isEmpty();
    }

    /**
     * 該島目前在場玩家數。
     */
    public int playerCount(UUID islandUuid) {
        if (islandUuid == null) {
            return 0;
        }
        Set<UUID> set = playersByIsland.get(islandUuid);
        return set == null ? 0 : set.size();
    }

    /**
     * 玩家目前被計入的島嶼 UUID（不在任何島則為 {@code null}）。
     */
    public UUID currentIsland(UUID playerUuid) {
        return islandByPlayer.get(playerUuid);
    }

    /**
     * 目前所有「啟用中（有人）」的島嶼 UUID 快照。
     */
    public Set<UUID> activeIslands() {
        return new HashSet<>(playersByIsland.keySet());
    }

    /**
     * 清空所有狀態（重新計算前使用）。
     */
    public void clear() {
        playersByIsland.clear();
        islandByPlayer.clear();
    }

    /**
     * 純函式：在給定「暫停系統是否啟用」與「該島是否有人在場」下，是否應讓無人值守的插件活動運作。
     *
     * <p>暫停系統未啟用時一律允許運作；啟用時僅在島上有人時運作（無人即暫停）。</p>
     */
    public static boolean shouldRunUnattended(boolean pauseEnabled, boolean active) {
        return !pauseEnabled || active;
    }
}
