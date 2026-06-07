package com.strawserver.strawskyblock.economy;

import org.bukkit.OfflinePlayer;

/**
 * 經濟系統抽象介面。建立島嶼免費，但保留 hook 供未來付費功能使用。
 */
public interface EconomyHook {

    boolean isEnabled();

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);
}
