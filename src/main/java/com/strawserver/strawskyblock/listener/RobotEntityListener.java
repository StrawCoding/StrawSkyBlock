package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.robot.Robot;
import com.strawserver.strawskyblock.robot.RobotItem;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 小機器人盔甲架小人的維護、保護與互動：
 * <ul>
 *   <li>區塊載入時補上盔甲架並清除孤兒。</li>
 *   <li>禁止其他來源破壞機器人盔甲架，並禁止玩家操作裝備。</li>
 *   <li>右鍵地面：放置手中的機器人物品（部署）。</li>
 *   <li>右鍵小人：進入「連結箱子」模式，下一次點擊箱子即綁定並自動開始挖礦。</li>
 *   <li>左鍵小人：拿起機器人（變回可放置物品，保留等級）。</li>
 * </ul>
 */
public class RobotEntityListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public RobotEntityListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getRobotService().handleChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (plugin.getRobotService().isRobotStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * 機器人盔甲架不受一般傷害；若是玩家左鍵攻擊，則視為「拿起」收回背包。
     *
     * <p>必須以 {@code ignoreCancelled = false} 且最早優先序（LOWEST）處理：盔甲架設為 invulnerable，
     * 玩家左鍵攻擊時 {@link EntityDamageByEntityEvent} 會因「無敵」被預先標記為已取消；
     * 另一個 {@link EntityDamageListener}（保護判定）也可能先行取消。若沿用 {@code ignoreCancelled = true}，
     * 本處理會被略過，導致擁有者左鍵完全無法拿取（治本：不論是否已被取消都接手，並由本處理統一取消事件）。</p>
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!plugin.getRobotService().isRobotStand(entity)) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player player)) {
            return;
        }
        Robot robot = plugin.getRobotService().getRobotByStand(entity);
        if (robot == null) {
            entity.remove();
            return;
        }
        Island island = plugin.getIslandService().getCache().getByUuid(robot.getIslandUuid());
        if (island == null || !island.getRole(player.getUniqueId()).isTrusted()) {
            plugin.getMessageManager().send(player, "robot.not-trusted");
            return;
        }
        plugin.getRobotService().pickUp(player, robot);
        plugin.getMessageManager().send(player, "robot.picked-up");
    }

    /**
     * 右鍵小人：進入「連結箱子」模式。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractStand(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!plugin.getRobotService().isRobotStand(entity)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Robot robot = plugin.getRobotService().getRobotByStand(entity);
        if (robot == null) {
            entity.remove();
            return;
        }
        Island island = plugin.getIslandService().getCache().getByUuid(robot.getIslandUuid());
        if (island == null || !island.getRole(player.getUniqueId()).isTrusted()) {
            plugin.getMessageManager().send(player, "robot.not-trusted");
            return;
        }
        plugin.getRobotService().beginChestBind(player.getUniqueId(), robot.locationKey());
        plugin.getMessageManager().send(player, "robot.bind-start");
    }

    /**
     * 右鍵方塊：連結箱子模式優先；否則嘗試放置手中的機器人物品。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Player player = event.getPlayer();

        // 1. 連結箱子模式
        if (plugin.getRobotService().isAwaitingChestBind(player.getUniqueId())) {
            event.setCancelled(true);
            handleChestBind(player, clicked);
            return;
        }

        // 2. 放置機器人物品
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (RobotItem.isRobotItem(plugin, inHand)) {
            event.setCancelled(true);
            handlePlace(player, clicked, event.getBlockFace(), inHand);
        }
    }

    private void handleChestBind(Player player, Block clicked) {
        String robotKey = plugin.getRobotService().consumeChestBind(player.getUniqueId());
        if (robotKey == null) {
            return;
        }
        Robot robot = plugin.getRobotService().getByLocationKey(robotKey);
        if (robot == null) {
            plugin.getMessageManager().send(player, "robot.none");
            return;
        }
        if (!(clicked.getState() instanceof Container)) {
            plugin.getMessageManager().send(player, "robot.chest-not-container");
            return;
        }
        Island island = plugin.getIslandService().getCache().getByUuid(robot.getIslandUuid());
        if (island == null || !island.contains(clicked.getLocation())) {
            plugin.getMessageManager().send(player, "robot.chest-outside");
            return;
        }
        robot.setChest(clicked.getX(), clicked.getY(), clicked.getZ());
        // 綁定箱子後自動開始挖礦。
        robot.setActive(true);
        robot.setChestFullNotified(false);
        plugin.getRobotService().saveAsync(robot);
        plugin.getMessageManager().send(player, "robot.chest-set",
                MessageManager.placeholders(
                        "x", String.valueOf(clicked.getX()),
                        "y", String.valueOf(clicked.getY()),
                        "z", String.valueOf(clicked.getZ())));
        plugin.getMessageManager().send(player, "robot.auto-started");
    }

    private void handlePlace(Player player, Block clicked, org.bukkit.block.BlockFace face, ItemStack inHand) {
        Block target = clicked.getRelative(face);
        Island island = plugin.getIslandService().getByLocation(target.getLocation());
        if (island == null) {
            plugin.getMessageManager().send(player, "robot.place-not-on-island");
            return;
        }
        if (!island.getRole(player.getUniqueId()).isTrusted()) {
            plugin.getMessageManager().send(player, "robot.not-trusted");
            return;
        }
        // 該位置已有機器人。
        if (plugin.getRobotService().getByLocation(
                target.getWorld().getName(), target.getX(), target.getY(), target.getZ()) != null) {
            plugin.getMessageManager().send(player, "robot.already-exists");
            return;
        }
        // 依 LuckPerms 上限檢查玩家可部署數量。
        int limit = plugin.getRobotService().getRobotLimit(player);
        int owned = plugin.getRobotService().countByOwner(player.getUniqueId());
        if (owned >= limit) {
            plugin.getMessageManager().send(player, "robot.limit-reached",
                    MessageManager.placeholders("max", String.valueOf(limit)));
            return;
        }
        int level = RobotItem.getLevel(plugin, inHand);
        // 小人面向放置玩家的當前朝向。
        float yaw = player.getLocation().getYaw();
        plugin.getRobotService().createRobot(island, player.getUniqueId(),
                target.getX(), target.getY(), target.getZ(), level, yaw);
        inHand.setAmount(inHand.getAmount() - 1);
        plugin.getMessageManager().send(player, "robot.placed",
                MessageManager.placeholders(
                        "x", String.valueOf(target.getX()),
                        "y", String.valueOf(target.getY()),
                        "z", String.valueOf(target.getZ())));
    }
}
