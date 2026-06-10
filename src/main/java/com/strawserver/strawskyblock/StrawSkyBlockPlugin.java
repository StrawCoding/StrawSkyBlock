package com.strawserver.strawskyblock;

import com.strawserver.strawskyblock.command.IslandCommand;
import com.strawserver.strawskyblock.config.ConfigManager;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.database.DatabaseManager;
import com.strawserver.strawskyblock.diagnostic.DiagnosticService;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.shop.ShopService;
import com.strawserver.strawskyblock.economy.VaultEconomyHook;
import com.strawserver.strawskyblock.generator.AnimalSpawnService;
import com.strawserver.strawskyblock.generator.CobbleGeneratorService;
import com.strawserver.strawskyblock.island.IslandOccupancyService;
import com.strawserver.strawskyblock.island.IslandService;
import com.strawserver.strawskyblock.listener.BlockBreakListener;
import com.strawserver.strawskyblock.listener.BlockFromToListener;
import com.strawserver.strawskyblock.listener.BlockPlaceListener;
import com.strawserver.strawskyblock.listener.CreatureSpawnListener;
import com.strawserver.strawskyblock.listener.EntityDamageListener;
import com.strawserver.strawskyblock.listener.InventoryClickListener;
import com.strawserver.strawskyblock.listener.IslandOccupancyListener;
import com.strawserver.strawskyblock.listener.NetherPortalListener;
import com.strawserver.strawskyblock.listener.PlayerInteractListener;
import com.strawserver.strawskyblock.listener.PlayerJoinListener;
import com.strawserver.strawskyblock.listener.PlayerRespawnListener;
import com.strawserver.strawskyblock.listener.RobotEntityListener;
import com.strawserver.strawskyblock.listener.SpawnCommandListener;
import com.strawserver.strawskyblock.listener.WorldProtectionListener;
import com.strawserver.strawskyblock.listener.VoidProtectionListener;
import com.strawserver.strawskyblock.placeholder.PlaceholderHook;
import com.strawserver.strawskyblock.protection.ProtectionService;
import com.strawserver.strawskyblock.robot.RobotService;
import com.strawserver.strawskyblock.util.TeleportActivityTracker;
import com.strawserver.strawskyblock.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StrawSkyBlock 主類別，負責生命週期與所有元件的組裝。
 */
public final class StrawSkyBlockPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DiagnosticService diagnosticService;
    private DatabaseManager databaseManager;
    private WorldManager worldManager;
    private IslandService islandService;
    private ProtectionService protectionService;
    private CobbleGeneratorService cobbleGeneratorService;
    private AnimalSpawnService animalSpawnService;
    private RobotService robotService;
    private EconomyHook economyHook;
    private ShopService shopService;
    private TeleportActivityTracker teleportActivityTracker;
    private IslandOccupancyService islandOccupancyService;

    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad() {
        // v1.0.29：原版主世界（level-name，如 world）於伺服器啟動、onEnable 之前即生成，
        // Bukkit 會在該階段呼叫 getDefaultWorldGenerator 取得自訂生成器。因此必須在 onLoad
        // 先備妥 ConfigManager 與 WorldManager，否則主世界拿不到虛空生成器（configManager 為 null）。
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.worldManager = new WorldManager(this);
    }

    @Override
    public void onEnable() {
        if (this.configManager == null) {
            this.configManager = new ConfigManager(this);
            this.configManager.load();
        }

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.diagnosticService = new DiagnosticService(this);
        this.teleportActivityTracker = new TeleportActivityTracker();

        this.databaseManager = new DatabaseManager(this);
        try {
            this.databaseManager.connect();
        } catch (SQLException e) {
            getLogger().severe("無法連線到 MySQL，插件停用：" + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (this.worldManager == null) {
            this.worldManager = new WorldManager(this);
        }

        this.islandService = new IslandService(this);
        this.protectionService = new ProtectionService(this);
        this.cobbleGeneratorService = new CobbleGeneratorService(this);
        this.animalSpawnService = new AnimalSpawnService(this);
        this.robotService = new RobotService(this);
        this.shopService = new ShopService(this);
        this.islandOccupancyService = new IslandOccupancyService(this);

        registerListeners();
        registerCommands();

        // v1.0.29：本插件為 load: STARTUP（早於預設世界載入，使虛空生成器能套到原版世界 world/
        // world_nether/world_the_end）。但 STARTUP 階段 Paper 禁止建立額外世界，且 Vault／
        // PlaceholderAPI 等軟相依尚未啟用、預設世界也尚未載入。因此把「建立 SkyBlock 世界、淨空
        // 原版世界、經濟／佔位符掛鉤、載入島嶼／機器人並啟動排程」全部延後到伺服器完全啟動後的
        // 第一個 tick 執行（此時所有插件已啟用、世界已載入、且允許建立世界）。
        Bukkit.getScheduler().runTask(this, this::finishStartup);

        getLogger().info("StrawSkyBlock 已啟用（待伺服器啟動完成後完成初始化）。");
    }

    /**
     * 伺服器完全啟動後（第一個 tick）執行的延後初始化。
     *
     * <p>包含必須在啟動完成後才能進行的操作：建立 SkyBlock 專用世界（STARTUP 階段禁止建立世界）、
     * 淨空伺服器原版世界、掛鉤 Vault／PlaceholderAPI（此時才啟用），以及載入島嶼／機器人並啟動排程。</p>
     */
    private void finishStartup() {
        worldManager.loadOrCreateIslandWorld();
        worldManager.loadOrCreateNetherWorld();
        worldManager.setupVanillaVoidWorlds();

        setupEconomy();
        setupPlaceholders();

        cobbleGeneratorService.reload();
        cobbleGeneratorService.start();
        animalSpawnService.reload();

        islandService.loadAll();
        robotService.loadAll();
        robotService.start();

        // v1.0.39：島嶼快取載入完成後，重新計算所有線上玩家的在場狀態，使佔用狀態正確。
        islandOccupancyService.recomputeAll();

        getLogger().info("StrawSkyBlock 啟動後初始化完成。");
    }

    @Override
    public void onDisable() {
        if (robotService != null) {
            robotService.stop();
        }
        if (cobbleGeneratorService != null) {
            cobbleGeneratorService.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("StrawSkyBlock 已停用。");
    }

    private void setupEconomy() {
        if (!configManager.isEconomyEnabled()) {
            return;
        }
        VaultEconomyHook hook = new VaultEconomyHook();
        if (hook.setup()) {
            this.economyHook = hook;
            getLogger().info("已掛鉤 Vault 經濟系統。");
        } else {
            getLogger().info("未偵測到 Vault 經濟系統，經濟功能停用。");
        }
    }

    private void setupPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("已註冊 PlaceholderAPI 變數。");
        }
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new InventoryClickListener(), this);
        pm.registerEvents(new BlockFromToListener(this), this);
        pm.registerEvents(new BlockBreakListener(this), this);
        pm.registerEvents(new BlockPlaceListener(this), this);
        pm.registerEvents(new PlayerInteractListener(this), this);
        pm.registerEvents(new EntityDamageListener(this), this);
        pm.registerEvents(new CreatureSpawnListener(this), this);
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new PlayerRespawnListener(this), this);
        pm.registerEvents(new SpawnCommandListener(this), this);
        pm.registerEvents(new WorldProtectionListener(this), this);
        pm.registerEvents(new VoidProtectionListener(this), this);
        pm.registerEvents(new NetherPortalListener(this), this);
        pm.registerEvents(new RobotEntityListener(this), this);
        pm.registerEvents(new IslandOccupancyListener(this), this);
        pm.registerEvents(teleportActivityTracker, this);
    }

    private void registerCommands() {
        IslandCommand executor = new IslandCommand(this);
        PluginCommand command = getCommand("is");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    /**
     * 重新載入設定與訊息，不影響玩家資料。
     */
    public void reloadAll() {
        configManager.load();
        messageManager.load();
        cobbleGeneratorService.reload();
        animalSpawnService.reload();
        if (robotService != null) {
            robotService.reload();
        }
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        if (configManager == null || worldManager == null) {
            return null;
        }
        // v1.0.29：原版世界（主世界/地獄/終界）若啟用虛空淨空，一律套用虛空生成器。
        if (configManager.isVanillaVoidWorld(worldName)) {
            return worldManager.getGenerator();
        }
        if (configManager.isVoidGenerator()
                && (worldName.equals(configManager.getIslandWorld())
                        || (configManager.isNetherEnabled()
                                && worldName.equals(configManager.getNetherWorld())))) {
            return worldManager.getGenerator();
        }
        return null;
    }

    // ---- bypass ----
    public boolean toggleBypass(UUID uuid) {
        if (bypassing.contains(uuid)) {
            bypassing.remove(uuid);
            return false;
        }
        bypassing.add(uuid);
        return true;
    }

    public boolean isBypassing(UUID uuid) {
        return bypassing.contains(uuid);
    }

    // ---- getters ----
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DiagnosticService getDiagnosticService() {
        return diagnosticService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public IslandService getIslandService() {
        return islandService;
    }

    public ProtectionService getProtectionService() {
        return protectionService;
    }

    public CobbleGeneratorService getCobbleGeneratorService() {
        return cobbleGeneratorService;
    }

    public AnimalSpawnService getAnimalSpawnService() {
        return animalSpawnService;
    }

    public RobotService getRobotService() {
        return robotService;
    }

    public EconomyHook getEconomyHook() {
        return economyHook;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public TeleportActivityTracker getTeleportActivityTracker() {
        return teleportActivityTracker;
    }

    public IslandOccupancyService getIslandOccupancyService() {
        return islandOccupancyService;
    }
}
