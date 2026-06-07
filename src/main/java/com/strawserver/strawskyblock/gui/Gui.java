package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 所有 GUI 的基底，透過 InventoryHolder 讓監聽器辨識並攔截點擊。
 */
public abstract class Gui implements InventoryHolder {

    protected final StrawSkyBlockPlugin plugin;
    protected final Inventory inventory;

    protected Gui(StrawSkyBlockPlugin plugin, int size, Component title) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        build(player);
        player.openInventory(inventory);
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /**
     * 重新繪製內容。
     */
    protected abstract void build(Player player);

    /**
     * 處理點擊事件（事件已被取消）。
     */
    public abstract void onClick(InventoryClickEvent event);
}
