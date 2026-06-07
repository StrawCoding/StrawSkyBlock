package com.strawserver.strawskyblock.generator;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.WeightedRandom;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

/**
 * 刷石機鵝卵石掉落表，依 config generator.drops 建立權重抽取器。
 */
public class OreDropTable {

    private final StrawSkyBlockPlugin plugin;
    private WeightedRandom<Material> table = new WeightedRandom<>();

    public OreDropTable(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        WeightedRandom<Material> built = new WeightedRandom<>();
        Map<String, Double> drops = plugin.getConfigManager().getGeneratorDrops();
        for (Map.Entry<String, Double> entry : drops.entrySet()) {
            Material material = matchMaterial(entry.getKey());
            if (material == null) {
                plugin.getLogger().warning("[Generator] 未知掉落物材質：" + entry.getKey() + "，已略過。");
                continue;
            }
            built.add(material, entry.getValue());
        }
        this.table = built;
    }

    private Material matchMaterial(String key) {
        return Material.matchMaterial(key.toUpperCase(Locale.ROOT));
    }

    public ItemStack roll() {
        Material material = table.roll();
        if (material == null) {
            material = Material.COBBLESTONE;
        }
        return new ItemStack(material, 1);
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }
}
