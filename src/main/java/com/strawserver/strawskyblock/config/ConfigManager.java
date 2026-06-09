package com.strawserver.strawskyblock.config;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        mergeDefaults();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        validateGeneratorDrops();
    }

    private void mergeDefaults() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        InputStream defStream = plugin.getResource("config.yml");
        if (defStream == null) {
            return;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defStream, StandardCharsets.UTF_8));
        current.setDefaults(defaults);
        current.options().copyDefaults(true);
        try {
            current.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法合併新版 config.yml 預設值: " + e.getMessage());
        }
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

    // ---- diagnostics (錯誤診斷) ----
    public boolean isDiagnosticsWriteFile() {
        return config.getBoolean("diagnostics.write-file", true);
    }

    public boolean isDiagnosticsNotifyAdmins() {
        return config.getBoolean("diagnostics.notify-admins", true);
    }

    public int getDiagnosticsMaxRecords() {
        return Math.max(1, config.getInt("diagnostics.max-records", 20));
    }

    public int getDiagnosticsStackFrames() {
        return Math.max(1, config.getInt("diagnostics.stack-frames", 8));
    }

    // ---- teleport (客戶端同步安全傳送) ----
    /**
     * 跨世界傳送策略（v1.0.12）：
     * <ul>
     *   <li>{@code PRELOAD_WAIT_SYNC}（預設）：預載並保留目的地區塊 → 等待數 tick →
     *       主執行緒同步傳送 → 等待客戶端活動穩定 → 必要時才走恢復／最終 kick。
     *       以「先確保區塊送達再傳送」為治本方向，降低客戶端卡在「載入地形」並減少重新同步轟炸。</li>
     *   <li>{@code LEGACY_ASYNC_RESYNC}：v1.0.11 的舊行為（teleportAsync + 多次重新同步 + 驗證）。</li>
     * </ul>
     */
    public String getTeleportStrategy() {
        String value = config.getString("teleport.strategy",
                com.strawserver.strawskyblock.util.IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC);
        return (value == null || value.isBlank())
                ? com.strawserver.strawskyblock.util.IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC
                : value.trim();
    }

    /**
     * PRELOAD_WAIT_SYNC 策略：預載／保留目的地區塊後，等待多少 tick 再執行同步傳送，
     * 讓伺服器有時間將區塊資料準備好並（透過 chunk ticket）保持載入。
     */
    public long getTeleportPreloadWaitTicks() {
        return Math.max(0L, config.getLong("teleport.preload-wait-ticks", 10L));
    }

    /**
     * PRELOAD_WAIT_SYNC 策略：同步傳送完成後，等待多少 tick 觀察客戶端是否完成維度交握
     * （送出主動封包／離開落點）後才進行成功後驗證；唯有此視窗過後仍可疑才會啟動恢復。
     */
    public long getTeleportClientActivityWaitTicks() {
        return Math.max(1L, config.getLong("teleport.client-activity-wait-ticks", 40L));
    }

    /**
     * PRELOAD_WAIT_SYNC 策略：跨世界傳送是否使用主執行緒同步傳送（{@code Player#teleport}）。
     * 關閉時改用 {@code teleportAsync}（仍走預載／等待／客戶端活動驗證流程）。
     */
    public boolean isTeleportUseSyncCrossWorldTeleport() {
        return config.getBoolean("teleport.use-sync-cross-world-teleport", true);
    }

    /** 跨世界進入空島世界後，是否延遲重新同步座標／區塊，避免客戶端卡在「載入地形」。 */
    public boolean isTeleportResyncEnabled() {
        return config.getBoolean("teleport.resync-enabled", true);
    }

    /** 傳送完成後多少 tick 執行重新同步（20 tick = 1 秒）。 */
    public long getTeleportResyncDelayTicks() {
        return Math.max(0L, config.getLong("teleport.resync-delay-ticks", 20L));
    }

    /** 重新同步後多少 tick 進行成功後狀態驗證；仍可疑則輸出診斷。 */
    public long getTeleportVerifyDelayTicks() {
        return Math.max(1L, config.getLong("teleport.verify-delay-ticks", 40L));
    }

    /** 跨世界傳送期間是否於落點加上區塊票證，確保區塊保持載入並送達客戶端。 */
    public boolean isTeleportChunkTicket() {
        return config.getBoolean("teleport.chunk-ticket", true);
    }

    /** 跨世界傳送後，延遲重新同步的次數（含第一次同座標重送）。 */
    public int getTeleportResyncMaxAttempts() {
        return Math.max(1, config.getInt("teleport.resync-max-attempts", 3));
    }

    /** 各次重新同步之間的間隔 tick。 */
    public long getTeleportResyncIntervalTicks() {
        return Math.max(1L, config.getLong("teleport.resync-interval-ticks", 10L));
    }

    /** 驗證仍可疑時，是否嘗試更強的卡住恢復（區塊重載 + 安全位移微調）。 */
    public boolean isTeleportRecoveryEnabled() {
        return config.getBoolean("teleport.recovery-enabled", true);
    }

    /** 卡住恢復後，再次驗證前等待的 tick。 */
    public long getTeleportRecoveryReverifyDelayTicks() {
        return Math.max(1L, config.getLong("teleport.recovery-reverify-delay-ticks", 20L));
    }

    /**
     * 跨世界傳送後，是否要求偵測到客戶端主動封包（移動／互動／指令／背包／揮手）
     * 才視為交握完成。開啟時，若驗證視窗內玩家仍停在落點且未觀察到任何客戶端活動，
     * 即判定為「伺服器狀態正常但客戶端未確認」（仍卡在載入地形），觸發恢復或診斷。
     */
    public boolean isTeleportClientAckRequired() {
        return config.getBoolean("teleport.client-ack-required", true);
    }

    /**
     * 卡住恢復後驗證仍可疑（跨世界）時，是否執行最終有界處置：強制重新連線（kick）。
     */
    public boolean isTeleportFinalFallbackKickEnabled() {
        return config.getBoolean("teleport.final-fallback-kick-enabled", true);
    }

    /** 最終恢復 kick 時顯示給玩家的繁體中文訊息（MiniMessage 格式）。 */
    public String getTeleportFinalFallbackKickMessage() {
        return config.getString("teleport.final-fallback-kick-message",
                "<yellow>偵測到跨世界傳送後客戶端維度交握未完成（畫面可能卡在「載入地形」）。</yellow>"
                        + "<newline><gray>已為您執行安全重新連線，請重新登入後再試。</gray>");
    }

    // ---- spawn intercept (/spawn from island world) ----
    public boolean isSpawnInterceptEnabled() {
        return config.getBoolean("spawn-intercept.enabled", true);
    }

    public String getSpawnInterceptPermission() {
        return config.getString("spawn-intercept.permission", "huskhomes.command.spawn");
    }

    public String getSpawnInterceptHuskHomesFile() {
        return config.getString("spawn-intercept.huskhomes-spawn-file", "plugins/HuskHomes/spawn.yml");
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

    // ---- robot (小機器人) ----
    public boolean isRobotEnabled() {
        return config.getBoolean("robot.enabled", true);
    }

    public int getRobotMaxPerIsland() {
        return config.getInt("robot.max-per-island", 1);
    }

    public boolean isRobotUseGeneratorDrops() {
        return config.getBoolean("robot.use-generator-drops", true);
    }

    public int getRobotVerticalRange() {
        return Math.max(0, config.getInt("robot.vertical-range", 1));
    }

    public int getRobotDefaultSpeedLevel() {
        return Math.max(1, config.getInt("robot.default-speed-level", 1));
    }

    public int getRobotDefaultLengthLevel() {
        return Math.max(1, config.getInt("robot.default-length-level", 1));
    }

    public long getRobotTaskPeriodTicks() {
        return Math.max(1L, config.getLong("robot.task-period-ticks", 1L));
    }

    public long getRobotFallbackInterval() {
        return Math.max(1L, config.getLong("robot.fallback-interval-ticks", 200L));
    }

    public int getRobotFallbackRange() {
        return Math.max(0, config.getInt("robot.fallback-range", 2));
    }

    public Map<Integer, Long> getRobotSpeedIntervals() {
        Map<Integer, Long> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("robot.speed-levels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Integer level = parseLevel(key);
                if (level != null) {
                    result.put(level, section.getLong(key + ".interval-ticks", getRobotFallbackInterval()));
                }
            }
        }
        return result;
    }

    public Map<Integer, Double> getRobotSpeedCosts() {
        Map<Integer, Double> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("robot.speed-levels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Integer level = parseLevel(key);
                if (level != null) {
                    result.put(level, section.getDouble(key + ".cost", 0.0D));
                }
            }
        }
        return result;
    }

    public Map<Integer, Integer> getRobotLengthRanges() {
        Map<Integer, Integer> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("robot.length-levels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Integer level = parseLevel(key);
                if (level != null) {
                    result.put(level, section.getInt(key + ".range", getRobotFallbackRange()));
                }
            }
        }
        return result;
    }

    public Map<Integer, Double> getRobotLengthCosts() {
        Map<Integer, Double> result = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("robot.length-levels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Integer level = parseLevel(key);
                if (level != null) {
                    result.put(level, section.getDouble(key + ".cost", 0.0D));
                }
            }
        }
        return result;
    }

    private Integer parseLevel(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[Robot] 無效的等級鍵值：" + key + "，已略過。");
            return null;
        }
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
