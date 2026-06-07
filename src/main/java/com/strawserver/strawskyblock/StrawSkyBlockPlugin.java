package com.strawserver.strawskyblock;

import com.strawserver.strawskyblock.command.IslandCommand;
import com.strawserver.strawskyblock.config.ConfigManager;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.database.DatabaseManager;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.economy.VaultEconomyHook;
import com.strawserver.strawskyblock.generator.AnimalSpawnService;
import com.strawserver.strawskyblock.generator.CobbleGeneratorService;
import com.strawserver.strawskyblock.island.IslandService;
import com.strawserver.strawskyblock.listener.BlockBreakListener;
import com.strawserver.strawskyblock.listener.BlockFromToListener;
import com.strawserver.strawskyblock.listener.BlockPlaceListener;
import com.strawserver.strawskyblock.listener.CreatureSpawnListener;
import com.strawserver.strawskyblock.listener.EntityDamageListener;
import com.strawserver.strawskyblock.listener.InventoryClickListener;
import com.strawserver.strawskyblock.listener.PlayerInteractListener;
import com.strawserver.strawskyblock.listener.PlayerJoinListener;
import com.strawserver.strawskyblock.listener.WorldProtectionListener;
import com.strawserver.strawskyblock.placeholder.PlaceholderHook;
import com.strawserver.strawskyblock.protection.ProtectionService;
import com.strawserver.strawskyblock.robot.RobotService;
import com.strawserver.strawskyblock.world.WorldManager;
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
    private DatabaseManager databaseManager;
    private WorldManager worldManager;
    private IslandService islandService;
    private ProtectionService protectionService;
    private CobbleGeneratorService cobbleGeneratorService;
    private AnimalSpawnService animalSpawnService;
    private RobotService robotService;
    private EconomyHook economyHook;

    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.databaseManager = new DatabaseManager(this);
        try {
            this.databaseManager.connect();
        } catch (SQLException e) {
            getLogger().severe("無法連線到 MySQL，插件停用：" + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.worldManager = new WorldManager(this);
        this.worldManager.loadOrCreateIslandWorld();

        this.islandService = new IslandService(this);
        this.protectionService = new ProtectionService(this);
        this.cobbleGeneratorService = new CobbleGeneratorService(this);
        this.cobbleGeneratorService.reload();
        this.cobbleGeneratorService.start();
        this.animalSpawnService = new AnimalSpawnService(this);
        this.animalSpawnService.reload();
        this.robotService = new RobotService(this);

        setupEconomy();
        setupPlaceholders();
        registerListeners();
        registerCommands();

        this.islandService.loadAll();
        this.robotService.loadAll();
        this.robotService.start();

        getLogger().info("StrawSkyBlock 已啟用。");
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
        pm.registerEvents(new WorldProtectionListener(this), this);
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
        if (configManager != null && worldManager != null
                && worldName.equals(configManager.getIslandWorld()) && configManager.isVoidGenerator()) {
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
}
