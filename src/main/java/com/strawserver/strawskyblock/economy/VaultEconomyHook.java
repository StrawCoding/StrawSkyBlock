package com.strawserver.strawskyblock.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 經濟橋接。若伺服器未安裝 Vault / 經濟插件則 isEnabled() 為 false。
 */
public class VaultEconomyHook implements EconomyHook {

    private Economy economy;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    @Override
    public boolean isEnabled() {
        return economy != null;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return isEnabled() && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return isEnabled() ? economy.format(amount) : String.valueOf(amount);
    }
}
