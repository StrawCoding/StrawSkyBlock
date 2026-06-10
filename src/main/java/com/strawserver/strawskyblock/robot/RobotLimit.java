package com.strawserver.strawskyblock.robot;

import java.util.Collection;

/**
 * 機器人數量上限解析（純邏輯，不依賴 Bukkit）。
 *
 * <p>上限由 LuckPerms 權限節點 {@code strawskyblock.robot.limit.<n>} 控制：
 * 取玩家所有此類節點中的最大數值；若玩家沒有任何此類節點，則回退到設定檔的預設值（預設 5）。</p>
 */
public final class RobotLimit {

    public static final String PERMISSION_PREFIX = "strawskyblock.robot.limit.";

    private RobotLimit() {
    }

    /**
     * 由玩家已授予的權限節點集合解析上限。
     *
     * @param defaultLimit  無權限節點時的預設上限。
     * @param grantedPermissions 玩家「值為 true」的有效權限節點。
     * @return 解析後的上限（至少 0）。
     */
    public static int resolve(int defaultLimit, Collection<String> grantedPermissions) {
        int fallback = Math.max(0, defaultLimit);
        if (grantedPermissions == null || grantedPermissions.isEmpty()) {
            return fallback;
        }
        int max = -1;
        for (String perm : grantedPermissions) {
            if (perm == null) {
                continue;
            }
            String lower = perm.toLowerCase(java.util.Locale.ROOT);
            if (!lower.startsWith(PERMISSION_PREFIX)) {
                continue;
            }
            String suffix = lower.substring(PERMISSION_PREFIX.length());
            try {
                int value = Integer.parseInt(suffix.trim());
                if (value >= 0) {
                    max = Math.max(max, value);
                }
            } catch (NumberFormatException ignored) {
                // 非數值節點（例如 limit.*）略過。
            }
        }
        return max < 0 ? fallback : max;
    }

    /**
     * 純邏輯：判斷玩家是否還能再放置一台機器人。
     *
     * <p>當 {@code bypass} 為 {@code true}（持有 {@code strawskyblock.admin.bypass}）時，
     * 無視數量上限一律放行。</p>
     *
     * @param ownedCount 玩家目前已部署的機器人數量。
     * @param limit      可部署上限（&le;0 表示不限）。
     * @param bypass     是否擁有管理員繞過權限。
     * @return 是否允許再放置一台。
     */
    public static boolean allowsPlacement(int ownedCount, int limit, boolean bypass) {
        if (bypass) {
            return true;
        }
        if (limit <= 0) {
            return true;
        }
        return ownedCount < limit;
    }
}
