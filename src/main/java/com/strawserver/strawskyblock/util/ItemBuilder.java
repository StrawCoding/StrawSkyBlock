package com.strawserver.strawskyblock.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 流暢式的 ItemStack 建構工具，主要供 GUI 使用。
 */
public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.meta = this.itemStack.getItemMeta();
    }

    public ItemBuilder name(String miniMessage) {
        if (meta != null) {
            meta.displayName(MiniMessageUtil.item(miniMessage));
        }
        return this;
    }

    public ItemBuilder name(Component component) {
        if (meta != null) {
            meta.displayName(component);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : lines) {
                lore.add(MiniMessageUtil.item(line));
            }
            meta.lore(lore);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        if (meta != null) {
            meta.lore(lines);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        return this;
    }

    public ItemBuilder hideAttributes() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
}
