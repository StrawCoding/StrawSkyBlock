package com.strawserver.strawskyblock.util;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.Locale;

/**
 * 管理員全域繞過（admin bypass）的單一真相來源。
 *
 * <p>持有 {@link #PERMISSION} 權限的對象（預設 op）可無視 StrawSkyBlock 自行加諸的
 * 限制／上限／費用，例如：機器人數量上限、機器人購買／升級費用、機器人放置／拿取的
 * 信任成員限制等。</p>
 *
 * <p>注意：此繞過只放行「插件限制」，不會跳過資料完整性或安全性檢查
 * （如：放置目標必須位於某座島嶼、座標已存在機器人、虛空防護傳送等）。</p>
 *
 * <p>主控台（Console）與命令方塊（CommandBlock）天生對所有權限回傳 true，
 * 因此於適用之處自然取得繞過。</p>
 */
public final class AdminBypass {

    /** 全域繞過權限節點（plugin.yml 預設 op）。 */
    public static final String PERMISSION = "strawskyblock.admin.bypass";

    private AdminBypass() {
    }

    /**
     * 判斷指令來源（玩家／主控台／命令方塊）是否擁有全域繞過權限。
     *
     * @param sender 指令來源；{@code null} 視為無繞過。
     * @return 是否可繞過插件限制。
     */
    public static boolean hasBypass(CommandSender sender) {
        return sender != null && sender.hasPermission(PERMISSION);
    }

    /**
     * 純邏輯：由「值為 true 的有效權限節點集合」判斷是否取得繞過權限。
     *
     * <p>與 Bukkit 解耦，供單元測試與非 Bukkit 情境使用。</p>
     *
     * @param grantedPermissions 玩家被授予（值為 true）的權限節點集合。
     * @return 是否包含繞過權限節點。
     */
    public static boolean grantedBy(Collection<String> grantedPermissions) {
        if (grantedPermissions == null) {
            return false;
        }
        for (String perm : grantedPermissions) {
            if (perm != null && perm.trim().toLowerCase(Locale.ROOT).equals(PERMISSION)) {
                return true;
            }
        }
        return false;
    }
}
