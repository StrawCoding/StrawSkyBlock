package com.strawserver.strawskyblock.config;

import com.strawserver.strawskyblock.util.IslandTeleportHelper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驗證 v1.0.12 新增的跨世界傳送策略設定鍵：getter 預設值、自訂值，
 * 以及打包 config.yml 內建預設與 getter 預設一致（驗收條件 6）。
 */
class ConfigManagerTest {

    private static ConfigManager withConfig(YamlConfiguration yaml) {
        ConfigManager manager = new ConfigManager(null);
        try {
            Field field = ConfigManager.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(manager, yaml);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("無法注入測試用 config", e);
        }
        return manager;
    }

    @Test
    void strategyGettersFallBackToTreatRootDefaultsWhenKeysAbsent() {
        ConfigManager manager = withConfig(new YamlConfiguration());
        assertEquals(IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC, manager.getTeleportStrategy());
        assertEquals(10L, manager.getTeleportPreloadWaitTicks());
        assertEquals(40L, manager.getTeleportClientActivityWaitTicks());
        assertTrue(manager.isTeleportUseSyncCrossWorldTeleport());
        // v1.0.11 最終 fallback 設定必須保留。
        assertTrue(manager.isTeleportFinalFallbackKickEnabled());
        assertNotNull(manager.getTeleportFinalFallbackKickMessage());
    }

    @Test
    void blankStrategyValueFallsBackToPreloadWaitSync() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("teleport.strategy", "   ");
        assertEquals(IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC,
                withConfig(yaml).getTeleportStrategy());
    }

    @Test
    void strategyGettersReadCustomValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("teleport.strategy", IslandTeleportHelper.STRATEGY_LEGACY_ASYNC_RESYNC);
        yaml.set("teleport.preload-wait-ticks", 5L);
        yaml.set("teleport.client-activity-wait-ticks", 60L);
        yaml.set("teleport.use-sync-cross-world-teleport", false);

        ConfigManager manager = withConfig(yaml);
        assertEquals(IslandTeleportHelper.STRATEGY_LEGACY_ASYNC_RESYNC, manager.getTeleportStrategy());
        assertEquals(5L, manager.getTeleportPreloadWaitTicks());
        assertEquals(60L, manager.getTeleportClientActivityWaitTicks());
        assertFalse(manager.isTeleportUseSyncCrossWorldTeleport());
    }

    @Test
    void clientActivityWaitIsClampedToAtLeastOneTick() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("teleport.client-activity-wait-ticks", 0L);
        yaml.set("teleport.preload-wait-ticks", -3L);
        ConfigManager manager = withConfig(yaml);
        assertEquals(1L, manager.getTeleportClientActivityWaitTicks());
        assertEquals(0L, manager.getTeleportPreloadWaitTicks());
    }

    @Test
    void bundledConfigContainsStrategyDefaultsMatchingGetters() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "打包的 config.yml 必須存在於 classpath");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigManager manager = withConfig(yaml);
            assertEquals(IslandTeleportHelper.STRATEGY_PRELOAD_WAIT_SYNC, manager.getTeleportStrategy());
            assertEquals(10L, manager.getTeleportPreloadWaitTicks());
            assertEquals(40L, manager.getTeleportClientActivityWaitTicks());
            assertTrue(manager.isTeleportUseSyncCrossWorldTeleport());
            assertTrue(manager.isTeleportFinalFallbackKickEnabled());
            assertNotNull(manager.getTeleportFinalFallbackKickMessage());
        }
    }
}
