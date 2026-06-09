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
 * <p>透過 PersistentDataContainer 標記為機器人物品，並保存速度 / 範圍等級，
 * 讓玩家拿起再放置時保留既有升級。</p>
 */
public final class RobotItem {

    private RobotItem() {
    }

    private static NamespacedKey markerKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item");
    }

    private static NamespacedKey speedKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item_speed");
    }

    private static NamespacedKey lengthKey(StrawSkyBlockPlugin plugin) {
        return new NamespacedKey(plugin, "robot_item_length");
    }

    /**
     * 建立一個機器人物品。
     */
    public static ItemStack create(StrawSkyBlockPlugin plugin, int speedLevel, int lengthLevel) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessageUtil.parse("<aqua><bold>⚙ 小機器人").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    MiniMessageUtil.parse("<gray>放置於自己的島上以部署。").decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    MiniMessageUtil.parse("<gray>速度等級：<yellow>L" + speedLevel).decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    MiniMessageUtil.parse("<gray>範圍等級：<yellow>L" + lengthLevel).decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    MiniMessageUtil.parse("<dark_gray>右鍵地面放置").decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
            pdc.set(speedKey(plugin), PersistentDataType.INTEGER, speedLevel);
            pdc.set(lengthKey(plugin), PersistentDataType.INTEGER, lengthLevel);
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

    public static int getSpeedLevel(StrawSkyBlockPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 1;
        }
        Integer v = meta.getPersistentDataContainer().get(speedKey(plugin), PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    public static int getLengthLevel(StrawSkyBlockPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 1;
        }
        Integer v = meta.getPersistentDataContainer().get(lengthKey(plugin), PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }
}
