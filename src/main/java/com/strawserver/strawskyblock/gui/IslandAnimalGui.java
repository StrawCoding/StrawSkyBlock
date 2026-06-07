package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

/**
 * 動物生成機率資訊 GUI（唯讀）。
 */
public class IslandAnimalGui extends Gui {

    private static final int BACK_SLOT = 49;

    public IslandAnimalGui(StrawSkyBlockPlugin plugin) {
        super(plugin, 54, MiniMessageUtil.parse("<green>挖石生成動物"));
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        Map<String, Integer> weights = plugin.getConfigManager().getAnimalWeights();
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        double globalChance = plugin.getConfigManager().getAnimalChance() * 100;

        set(4, new ItemBuilder(Material.BONE)
                .name("<green>每挖一個刷石機鵝卵石")
                .lore("<gray>觸發機率：<yellow>" + globalChance + "%").build());

        int slot = 9;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            if (slot >= 45) {
                break;
            }
            double pct = total > 0 ? (entry.getValue() * 100.0 / total) : 0;
            Material icon = iconFor(entry.getKey());
            set(slot, new ItemBuilder(icon)
                    .name("<yellow>" + entry.getKey())
                    .lore("<gray>權重：<white>" + entry.getValue(),
                            "<gray>相對機率：<green>" + String.format("%.1f", pct) + "%").build());
            slot++;
        }
        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回主選單").build());
    }

    private Material iconFor(String type) {
        return switch (type.toUpperCase()) {
            case "CHICKEN" -> Material.CHICKEN;
            case "COW" -> Material.BEEF;
            case "SHEEP" -> Material.WHITE_WOOL;
            case "PIG" -> Material.PORKCHOP;
            case "RABBIT" -> Material.RABBIT_FOOT;
            case "WOLF" -> Material.BONE;
            case "CAT" -> Material.COD;
            case "VILLAGER" -> Material.EMERALD;
            default -> Material.EGG;
        };
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getRawSlot() == BACK_SLOT) {
            new IslandMainGui(plugin).open((Player) event.getWhoClicked());
        }
    }
}
