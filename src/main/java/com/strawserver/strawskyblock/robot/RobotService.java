package com.strawserver.strawskyblock.robot;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小機器人服務：管理機器人的記憶體狀態、持久化，以及單一受控的挖掘排程。
 *
 * <p>設計重點：</p>
 * <ul>
 *   <li>每座島嶼最多 {@code robot.max-per-island} 台機器人（預設 1，以島嶼 UUID 為鍵）。</li>
 *   <li>機器人以一個原點座標作為掃描中心，並於原點生成一個「盔甲架小人」作為外觀，
 *       透過 PersistentDataContainer 標記其所屬島嶼以便重啟後辨識與清理。</li>
 *   <li>挖掘由單一 {@code runTaskTimer}（主執行緒）驅動，每台機器人依速度等級的間隔挖掘一格，
 *       掃描範圍受長度等級與島嶼邊界雙重限制，確保效能與安全。</li>
 *   <li>先確認可放入箱子才破壞方塊，避免任何掉落物遺失。</li>
 *   <li>所有資料庫操作於非同步執行緒；所有 Bukkit 操作於主執行緒。</li>
 * </ul>
 */
public class RobotService {

    private final StrawSkyBlockPlugin plugin;
    private final RobotRepository repository;
    private final Map<UUID, Robot> robotsByIsland = new ConcurrentHashMap<>();
    /** 盔甲架小人的 PDC 標記鍵，值為其所屬島嶼 UUID 字串。 */
    private final NamespacedKey robotKey;

    private RobotLevels levels;
    private BukkitTask task;
    /** 機器人資料是否已從資料庫載入完成；未載入前不對盔甲架做孤兒清理，避免誤刪。 */
    private volatile boolean robotsLoaded;

    public RobotService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
        this.repository = new RobotRepository(plugin);
        this.robotKey = new NamespacedKey(plugin, "robot_island");
        reload();
    }

    public RobotLevels getLevels() {
        return levels;
    }

    /**
     * 發送小機器人使用說明（/is robot help 與購買後皆會呼叫）。
     */
    public void sendHelp(org.bukkit.command.CommandSender sender) {
        plugin.getMessageManager().sendList(sender, "robot.help");
    }

    // =========================================================================
    // 盔甲架小人（實體外觀）
    // =========================================================================
    /**
     * 判斷實體是否為小機器人盔甲架。
     */
    public boolean isRobotStand(Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(robotKey, PersistentDataType.STRING);
    }

    private UUID getStandIsland(Entity entity) {
        String value = entity.getPersistentDataContainer().get(robotKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 確保機器人在其原點有一個盔甲架小人；若原點區塊已載入且尚無對應盔甲架則生成。
     * 必須於主執行緒呼叫。
     */
    public void ensureArmorStand(Robot robot) {
        World world = Bukkit.getWorld(robot.getWorldName());
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(robot.getOriginX() >> 4, robot.getOriginZ() >> 4)) {
            return;
        }
        if (findStand(world, robot) != null) {
            return;
        }
        spawnStand(world, robot);
    }

    private ArmorStand findStand(World world, Robot robot) {
        Chunk chunk = world.getChunkAt(robot.getOriginX() >> 4, robot.getOriginZ() >> 4);
        String islandStr = robot.getIslandUuid().toString();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ArmorStand
                    && islandStr.equals(entity.getPersistentDataContainer()
                    .get(robotKey, PersistentDataType.STRING))) {
                return (ArmorStand) entity;
            }
        }
        return null;
    }

    private void spawnStand(World world, Robot robot) {
        Location loc = new Location(world,
                robot.getOriginX() + 0.5, robot.getOriginY(), robot.getOriginZ() + 0.5);
        world.spawn(loc, ArmorStand.class, stand -> {
            stand.setSmall(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(true);
            stand.customName(MiniMessageUtil.parse("<aqua>⚙ 小機器人"));
            stand.getPersistentDataContainer().set(robotKey, PersistentDataType.STRING,
                    robot.getIslandUuid().toString());
            EntityEquipment equipment = stand.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(new ItemStack(Material.PLAYER_HEAD));
                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
            }
            stand.setRotation(0f, 0f);
        });
    }

    /**
     * 移除機器人的盔甲架小人（若所在區塊已載入）。必須於主執行緒呼叫。
     */
    public void removeArmorStand(Robot robot) {
        World world = Bukkit.getWorld(robot.getWorldName());
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(robot.getOriginX() >> 4, robot.getOriginZ() >> 4)) {
            return;
        }
        ArmorStand stand = findStand(world, robot);
        if (stand != null) {
            stand.remove();
        }
    }

    /**
     * 區塊載入時：為位於此區塊的機器人補上盔甲架，並清除無對應機器人的孤兒盔甲架。
     * 僅處理空島世界。必須於主執行緒呼叫。
     */
    public void handleChunkLoad(Chunk chunk) {
        if (!robotsLoaded) {
            return;
        }
        if (!chunk.getWorld().getName().equals(plugin.getConfigManager().getIslandWorld())) {
            return;
        }
        for (Robot robot : robotsByIsland.values()) {
            if (robot.getWorldName().equals(chunk.getWorld().getName())
                    && (robot.getOriginX() >> 4) == chunk.getX()
                    && (robot.getOriginZ() >> 4) == chunk.getZ()) {
                ensureArmorStand(robot);
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (!isRobotStand(entity)) {
                continue;
            }
            UUID island = getStandIsland(entity);
            if (island == null || !robotsByIsland.containsKey(island)) {
                entity.remove();
            }
        }
    }

    /**
     * 為所有原點區塊已載入的機器人補上盔甲架（伺服器載入後呼叫）。
     */
    public void ensureAllArmorStands() {
        for (Robot robot : robotsByIsland.values()) {
            ensureArmorStand(robot);
        }
    }

    public Robot getByIsland(UUID islandUuid) {
        return robotsByIsland.get(islandUuid);
    }

    public int countByOwner(UUID ownerUuid) {
        int count = 0;
        for (Robot robot : robotsByIsland.values()) {
            if (robot.getOwnerUuid().equals(ownerUuid)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 依目前 config 重建等級表。可於 reload 時呼叫。
     */
    public void reload() {
        this.levels = new RobotLevels(
                plugin.getConfigManager().getRobotSpeedIntervals(),
                plugin.getConfigManager().getRobotSpeedCosts(),
                plugin.getConfigManager().getRobotLengthRanges(),
                plugin.getConfigManager().getRobotLengthCosts(),
                plugin.getConfigManager().getRobotFallbackInterval(),
                plugin.getConfigManager().getRobotFallbackRange());
    }

    /**
     * 非同步載入所有機器人到記憶體。
     */
    public void loadAll() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Robot> robots = repository.loadAll();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    robotsByIsland.clear();
                    for (Robot robot : robots) {
                        robotsByIsland.put(robot.getIslandUuid(), robot);
                    }
                    robotsLoaded = true;
                    ensureAllArmorStands();
                    plugin.getLogger().info("已載入 " + robots.size() + " 台小機器人。");
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("載入小機器人失敗：" + e.getMessage());
            }
        });
    }

    public void start() {
        stop();
        long period = plugin.getConfigManager().getRobotTaskPeriodTicks();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // =========================================================================
    // 建立 / 設定 / 移除（皆於主執行緒呼叫，資料庫寫入非同步）
    // =========================================================================
    public Robot createRobot(Island island, UUID ownerUuid, int originX, int originY, int originZ) {
        Robot robot = new Robot(island.getIslandUuid(), ownerUuid, island.getWorldName(),
                originX, originY, originZ, null, null, null,
                levels.clampLevel(plugin.getConfigManager().getRobotDefaultSpeedLevel()),
                levels.clampLevel(plugin.getConfigManager().getRobotDefaultLengthLevel()),
                false);
        robotsByIsland.put(island.getIslandUuid(), robot);
        saveAsync(robot);
        ensureArmorStand(robot);
        return robot;
    }

    public void removeRobot(Robot robot) {
        removeArmorStand(robot);
        robotsByIsland.remove(robot.getIslandUuid());
        UUID islandUuid = robot.getIslandUuid();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.delete(islandUuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("刪除小機器人失敗：" + e.getMessage());
            }
        });
    }

    /**
     * 當島嶼被刪除時，連帶移除其機器人。
     */
    public void onIslandRemoved(UUID islandUuid) {
        Robot robot = robotsByIsland.remove(islandUuid);
        if (robot == null) {
            return;
        }
        removeArmorStand(robot);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.delete(islandUuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("刪除小機器人失敗：" + e.getMessage());
            }
        });
    }

    public void saveAsync(Robot robot) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.save(robot);
            } catch (SQLException e) {
                plugin.getLogger().warning("儲存小機器人失敗：" + e.getMessage());
            }
        });
    }

    // =========================================================================
    // 挖掘排程
    // =========================================================================
    private void tickAll() {
        if (!plugin.getConfigManager().isRobotEnabled() || robotsByIsland.isEmpty()) {
            return;
        }
        long period = plugin.getConfigManager().getRobotTaskPeriodTicks();
        for (Robot robot : robotsByIsland.values()) {
            if (!robot.isActive()) {
                continue;
            }
            robot.addTicks(period);
            long interval = levels.intervalTicks(robot.getSpeedLevel());
            if (robot.getTickCounter() < interval) {
                continue;
            }
            robot.resetTickCounter();
            try {
                tickRobot(robot);
            } catch (Exception e) {
                plugin.getLogger().warning("小機器人挖掘發生例外（島嶼 " + robot.getIslandUuid()
                        + "）：" + e.getMessage());
            }
        }
    }

    /**
     * 單台機器人的一次挖掘嘗試。必須於主執行緒呼叫。
     */
    private void tickRobot(Robot robot) {
        World world = Bukkit.getWorld(robot.getWorldName());
        if (world == null) {
            return;
        }
        // 僅在設定的空島世界運作。
        if (!robot.getWorldName().equals(plugin.getConfigManager().getIslandWorld())) {
            return;
        }
        Island island = plugin.getIslandService().getCache().getByUuid(robot.getIslandUuid());
        if (island == null) {
            return; // 島嶼可能已被刪除，靜默略過（清理由 onIslandRemoved 處理）。
        }

        // 原點所在區塊未載入則略過，避免強制載入造成效能負擔。
        if (!world.isChunkLoaded(robot.getOriginX() >> 4, robot.getOriginZ() >> 4)) {
            return;
        }

        // 必須先設定箱子。
        if (!robot.hasChest()) {
            disableWithNotice(robot, "robot.error-no-chest");
            return;
        }

        // 尋找掃描範圍內第一個（位於島嶼邊界內的）鵝卵石。
        Block target = findCobblestone(world, robot, island);
        if (target == null) {
            return; // 沒有可挖掘的鵝卵石。
        }

        // 檢查箱子。
        int cx = robot.getChestX();
        int cy = robot.getChestY();
        int cz = robot.getChestZ();
        if (!world.isChunkLoaded(cx >> 4, cz >> 4)) {
            return; // 箱子區塊未載入，略過本次。
        }
        Block chestBlock = world.getBlockAt(cx, cy, cz);
        BlockState state = chestBlock.getState();
        if (!(state instanceof Container container)) {
            // 箱子被移除或不是容器：停機並通知，避免無處存放。
            disableWithNotice(robot, "robot.error-chest-missing");
            return;
        }

        ItemStack drop = computeDrop();
        if (drop == null || drop.getType() == Material.AIR) {
            return;
        }

        Inventory inventory = container.getInventory();
        // 先模擬放入；amount 為 1，addItem 會全進或全不進。
        Map<Integer, ItemStack> leftover = inventory.addItem(drop.clone());
        if (!leftover.isEmpty()) {
            // 箱子已滿：不破壞方塊（不遺失任何物品），僅通知一次。
            if (!robot.isChestFullNotified()) {
                notifyOwner(robot, "robot.chest-full");
                robot.setChestFullNotified(true);
            }
            return;
        }
        robot.setChestFullNotified(false);

        // 成功存入後才破壞方塊。必須套用物理更新，否則鄰近水/岩漿不會流動，刷石機無法再生。
        Location targetLoc = target.getLocation();
        target.setType(Material.AIR, true);
        plugin.getCobbleGeneratorService().removeGeneratedCobblestone(targetLoc);

        boolean ore = drop.getType() != Material.COBBLESTONE;
        plugin.getIslandService().addStats(island, 1, 1, ore ? 1 : 0, 0);
    }

    private Block findCobblestone(World world, Robot robot, Island island) {
        int half = island.getSize() / 2;
        RobotScanArea area = RobotScanArea.around(
                robot.getOriginX(), robot.getOriginY(), robot.getOriginZ(),
                levels.range(robot.getLengthLevel()),
                plugin.getConfigManager().getRobotVerticalRange(),
                island.getCenterX(), island.getCenterZ(), half);
        if (area.isEmpty()) {
            return null;
        }
        int minY = Math.max(area.minY(), world.getMinHeight());
        int maxY = Math.min(area.maxY(), world.getMaxHeight() - 1);
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                if (!island.contains(x, z)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.COBBLESTONE) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private ItemStack computeDrop() {
        if (plugin.getConfigManager().isRobotUseGeneratorDrops()) {
            ItemStack rolled = plugin.getCobbleGeneratorService().rollDrop();
            if (rolled != null && rolled.getType() != Material.AIR) {
                return rolled;
            }
        }
        return new ItemStack(Material.COBBLESTONE, 1);
    }

    private void disableWithNotice(Robot robot, String messageKey) {
        robot.setActive(false);
        saveAsync(robot);
        notifyOwner(robot, messageKey);
    }

    private void notifyOwner(Robot robot, String messageKey) {
        Player owner = Bukkit.getPlayer(robot.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            plugin.getMessageManager().send(owner, messageKey,
                    MessageManager.placeholders(
                            "x", String.valueOf(robot.getOriginX()),
                            "y", String.valueOf(robot.getOriginY()),
                            "z", String.valueOf(robot.getOriginZ())));
        }
    }
}
