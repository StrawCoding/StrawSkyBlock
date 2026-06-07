package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Locale;
import java.util.Map;

/**
 * 礦物機率資訊 GUI（唯讀）。
 */
public class IslandGeneratorGui extends Gui {

    private static final int BACK_SLOT = 49;

    public IslandGeneratorGui(StrawSkyBlockPlugin plugin) {
        super(plugin, 54, MiniMessageUtil.parse("<white>刷石機礦物機率"));
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        Map<String, Double> drops = plugin.getConfigManager().getGeneratorDrops();
        int slot = 0;
        for (Map.Entry<String, Double> entry : drops.entrySet()) {
            if (slot >= 45) {
                break;
            }
            Material material = Material.matchMaterial(entry.getKey().toUpperCase(Locale.ROOT));
            if (material == null) {
                material = Material.PAPER;
            }
            set(slot, new ItemBuilder(material)
                    .name("<yellow>" + entry.getKey())
                    .lore("<gray>掉落機率：<green>" + entry.getValue() + "%").build());
            slot++;
        }
        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回主選單").build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getRawSlot() == BACK_SLOT) {
            new IslandMainGui(plugin).open((Player) event.getWhoClicked());
        }
    }
}
