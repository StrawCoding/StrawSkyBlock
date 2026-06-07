package com.strawserver.strawskyblock.config;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 包裝 config.yml 的存取，並在載入時做基本驗證（例如礦物機率總和）。
 */
public class ConfigManager {

    private final StrawSkyBlockPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        validateGeneratorDrops();
    }

    public FileConfiguration raw() {
        return config;
    }

    // ---- plugin ----
    public boolean isDebug() {
        return config.getBoolean("plugin.debug", false);
    }

    public void setDebug(boolean debug) {
        config.set("plugin.debug", debug);
        plugin.saveConfig();
    }

    public String getLanguage() {
        return config.getString("plugin.language", "zh_TW");
    }

    // ---- database ----
    public String getDbHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDbPort() {
        return config.getInt("database.port", 3306);
    }

    public String getDbName() {
        return config.getString("database.database", "straw_skyblock");
    }

    public String getDbUser() {
        return config.getString("database.username", "root");
    }

    public String getDbPassword() {
        return config.getString("database.password", "");
    }

    public int getDbPoolSize() {
        return config.getInt("database.pool-size", 10);
    }

    public boolean isDbUseSsl() {
        return config.getBoolean("database.use-ssl", false);
    }

    // ---- world ----
    public String getIslandWorld() {
        return config.getString("world.island-world", "straw_skyblock_world");
    }

    public boolean isVoidGenerator() {
        return config.getBoolean("world.void-generator", true);
    }

    public int getIslandSpacing() {
        return config.getInt("world.island-spacing", 1000);
    }

    public int getIslandSize() {
        return config.getInt("world.island-size", 200);
    }

    public int getIslandY() {
        return config.getInt("world.island-y", 100);
    }

    // ---- island ----
    public int getMaxIslandsPerPlayer() {
        return config.getInt("island.max-islands-per-player", 1);
    }

    public int getDeleteConfirmSeconds() {
        return config.getInt("island.delete-confirm-seconds", 15);
    }

    public int getSpawnOffsetX() {
        return config.getInt("template.default.spawn-offset.x", 0);
    }

    public int getSpawnOffsetY() {
        return config.getInt("template.default.spawn-offset.y", 3);
    }

    public int getSpawnOffsetZ() {
        return config.getInt("template.default.spawn-offset.z", 0);
    }

    // ---- generator ----
    public boolean isGeneratorEnabled() {
        return config.getBoolean("generator.enabled", true);
    }

    public boolean isOnlyGeneratedCobblestone() {
        return config.getBoolean("generator.only-generated-cobblestone", true);
    }

    public boolean isFortuneSupport() {
        return config.getBoolean("generator.fortune-support", false);
    }

    public Map<String, Double> getGeneratorDrops() {
        Map<String, Double> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("generator.drops");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key.toUpperCase(Locale.ROOT), section.getDouble(key));
            }
        }
        return result;
    }

    // ---- animal spawn ----
    public boolean isAnimalSpawnEnabled() {
        return config.getBoolean("animal-spawn.enabled", true);
    }

    public double getAnimalChance() {
        return config.getDouble("animal-spawn.chance", 0.01);
    }

    public int getAnimalCooldownSeconds() {
        return config.getInt("animal-spawn.cooldown-seconds-per-island", 30);
    }

    public int getMaxPassiveMobsPerIsland() {
        return config.getInt("animal-spawn.max-passive-mobs-per-island", 30);
    }

    public Map<String, Integer> getAnimalWeights() {
        Map<String, Integer> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("animal-spawn.animals");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key.toUpperCase(Locale.ROOT), section.getInt(key));
            }
        }
        return result;
    }

    // ---- protection default flags ----
    public boolean getDefaultFlag(String key, boolean fallback) {
        return config.getBoolean("protection.default-flags." + key, fallback);
    }

    // ---- economy ----
    public boolean isEconomyEnabled() {
        return config.getBoolean("economy.enabled", true);
    }

    public double getCreateIslandCost() {
        return config.getDouble("economy.create-island-cost", 0);
    }

    /**
     * 驗證礦物掉落機率總和，避免設定錯誤導致抽取偏差。
     */
    private void validateGeneratorDrops() {
        Map<String, Double> drops = getGeneratorDrops();
        if (drops.isEmpty()) {
            return;
        }
        double sum = drops.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 100.0D) > 0.5D) {
            plugin.getLogger().warning("[Config] generator.drops 機率總和為 " + sum
                    + "%（建議為 100%），系統仍會依權重抽取，但請檢查設定。");
        }
    }
}
