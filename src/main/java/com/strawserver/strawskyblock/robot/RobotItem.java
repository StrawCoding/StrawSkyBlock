package com.strawserver.strawskyblock.robot;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 小機器人的「可放置物品」形態。
 *
 * <p>透過 PersistentDataContainer 標記為機器人物品，並保存統一等級，
 * 讓玩家拿起再放置時保留既有升級。</p>
 */
public final class RobotItem {

    private RobotItem() {
    }

    private static NamespacedKey markerKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item");
    }

    private static NamespacedKey levelKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item_level");
    }

    // 舊版本（v1.0.24 以前）的速度 / 範圍鍵，僅用於讀取相容。
    private static NamespacedKey legacySpeedKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item_speed");
    }

    private static NamespacedKey legacyLengthKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item_length");
    }

    /**
     * 建立一個機器人物品。
     */
    public static ItemStack create(StrawSkyBlockPlugin plugin, int level) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessageUtil.parse("<aqua><bold>⚙ 小機器人 L" + level).decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    MiniMessageUtil.parse("<gray>放置於自己的島上以部署。").decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    MiniMessageUtil.parse("<gray>等級：<yellow>L" + level).decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    MiniMessageUtil.parse("<dark_gray>右鍵地面放置").decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
            pdc.set(levelKey(plugin), PersistentDataType.INTEGER, level);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isRobotItem(StrawSkyBlockPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(markerKey(plugin), PersistentDataType.BYTE);
    }

    /**
     * 讀取物品等級；相容舊版本（取舊速度 / 範圍鍵的較大值）。
     */
    public static int getLevel(StrawSkyBlockPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 1;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer v = pdc.get(levelKey(plugin), PersistentDataType.INTEGER);
        if (v != null) {
            return Math.max(1, v);
        }
        Integer speed = pdc.get(legacySpeedKey(plugin), PersistentDataType.INTEGER);
        Integer length = pdc.get(legacyLengthKey(plugin), PersistentDataType.INTEGER);
        if (speed != null || length != null) {
            return Math.max(1, Math.max(speed == null ? 1 : speed, length == null ? 1 : length));
        }
        return 1;
    }
}
