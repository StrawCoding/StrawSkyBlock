package com.strawserver.strawskyblock.generator;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.util.WeightedRandom;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 生成方塊模式（v1.0.32）使用的「礦石方塊」權重表：依 config {@code generator.ore-blocks} 建立。
 *
 * <p>與 {@link OreDropTable} 不同——本表抽出的是「方塊材質」（如 {@link Material#COAL_ORE}），
 * 供刷石機在生成鵝卵石時直接把方塊替換為對應礦石；玩家／機器人挖到的就是實際礦石方塊，
 * 原版掉落（含時運、精準採集）皆生效。</p>
 */
public class OreBlockTable {

    private final StrawSkyBlockPlugin plugin;
    private WeightedRandom<Material> table = new WeightedRandom<>();
    /** 本表涵蓋的所有方塊材質（含鵝卵石），供「是否為刷石機方塊」判定。 */
    private Set<Material> blockTypes = EnumSet.of(Material.COBBLESTONE);

    public OreBlockTable(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        WeightedRandom<Material> built = new WeightedRandom<>();
        Set<Material> types = EnumSet.of(Material.COBBLESTONE);
        Map<String, Double> blocks = plugin.getConfigManager().getGeneratorOreBlocks();
        for (Map.Entry<String, Double> entry : blocks.entrySet()) {
            Material material = Material.matchMaterial(entry.getKey().toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("[Generator] 未知或非方塊的礦石材質：" + entry.getKey() + "，已略過。");
                continue;
            }
            built.add(material, entry.getValue());
            types.add(material);
        }
        this.table = built;
        this.blockTypes = types;
    }

    /**
     * 依權重抽取一個礦石方塊材質；表為空時退回鵝卵石。
     */
    public Material roll() {
        Material material = table.roll();
        return material == null ? Material.COBBLESTONE : material;
    }

    /**
     * 該材質是否屬於刷石機方塊（鵝卵石或設定中的礦石方塊），供機器人掃描與破壞判定。
     */
    public boolean isGeneratorBlock(Material material) {
        return blockTypes.contains(material);
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }
}
