package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.robot.Robot;
import com.strawserver.strawskyblock.robot.RobotLevels;
import com.strawserver.strawskyblock.robot.UpgradeResult;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 單台機器人的升級 / 控制畫面（由機器人商城列表點選進入）。
 *
 * <p>每台機器人各自獨立升級：升一級同時提升挖掘速度與掃描範圍，需付出對應花費。</p>
 */
public class RobotUpgradeGui extends Gui {

    private static final int INFO_SLOT = 11;
    private static final int UPGRADE_SLOT = 13;
    private static final int TOGGLE_SLOT = 15;
    private static final int BACK_SLOT = 22;

    private final String robotKey;

    public RobotUpgradeGui(StrawSkyBlockPlugin plugin, String robotKey) {
        super(plugin, 27, MiniMessageUtil.parse("<gold>機器人升級"));
        this.robotKey = robotKey;
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        ItemBuilder filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");
        for (int i = 0; i < inventory.getSize(); i++) {
            set(i, filler.build());
        }

        Robot robot = plugin.getRobotService().getByLocationKey(robotKey);
        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回機器人列表").build());
        if (robot == null) {
            set(INFO_SLOT, new ItemBuilder(Material.BARRIER)
                    .name("<red>機器人已不存在")
                    .lore("<gray>它可能已被拿起或移除。").build());
            return;
        }

        RobotLevels levels = plugin.getRobotService().getLevels();
        int level = robot.getLevel();
        int max = levels.getMaxLevel();
        String status = robot.isActive() ? "<green>運作中"
                : (robot.hasChest() ? "<yellow>已停止" : "<red>未綁定箱子");

        set(INFO_SLOT, new ItemBuilder(Material.NETHERITE_PICKAXE)
                .name("<aqua>小機器人 <yellow>L" + level + " / L" + max)
                .lore(
                        "<gray>位置：<white>" + robot.getOriginX() + ", "
                                + robot.getOriginY() + ", " + robot.getOriginZ(),
                        "<gray>挖掘間隔：<white>" + levels.intervalTicks(level) + " tick",
                        "<gray>掃描半徑：<white>" + levels.range(level) + " 格",
                        "<gray>狀態：" + status)
                .hideAttributes()
                .build());

        // 升級按鈕。
        if (level >= max) {
            set(UPGRADE_SLOT, new ItemBuilder(Material.BARRIER)
                    .name("<gold>已達最高等級 L" + max)
                    .lore("<gray>這台機器人無法再升級。").build());
        } else {
            int next = level + 1;
            double cost = levels.upgradeCost(next);
            EconomyHook economy = plugin.getEconomyHook();
            String costText = cost <= 0 ? "免費"
                    : (economy != null ? economy.format(cost) : String.valueOf(cost));
            set(UPGRADE_SLOT, new ItemBuilder(Material.EMERALD)
                    .name("<green><bold>升級到 L" + next)
                    .lore(
                            "<gray>挖掘間隔：<white>" + levels.intervalTicks(level)
                                    + " <gray>→ <green>" + levels.intervalTicks(next) + " tick",
                            "<gray>掃描半徑：<white>" + levels.range(level)
                                    + " <gray>→ <green>" + levels.range(next) + " 格",
                            "",
                            "<gray>花費：<gold>" + costText,
                            "",
                            "<yellow>點擊升級")
                    .glow(true)
                    .build());
        }

        // 啟動 / 停止切換。
        if (robot.isActive()) {
            set(TOGGLE_SLOT, new ItemBuilder(Material.REDSTONE)
                    .name("<red>停止挖掘")
                    .lore("<gray>點擊讓這台機器人暫停。").build());
        } else {
            set(TOGGLE_SLOT, new ItemBuilder(Material.LIME_DYE)
                    .name("<green>開始挖掘")
                    .lore("<gray>點擊讓這台機器人開始挖掘。",
                            "<dark_gray>需先綁定箱子。").build());
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            new RobotShopGui(plugin).open(player);
            return;
        }
        if (slot == UPGRADE_SLOT) {
            attemptUpgrade(player);
            return;
        }
        if (slot == TOGGLE_SLOT) {
            toggleActive(player);
        }
    }

    private Robot requireRobot(Player player) {
        Robot robot = plugin.getRobotService().getByLocationKey(robotKey);
        if (robot == null) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "robot.none");
            return null;
        }
        return robot;
    }

    private void attemptUpgrade(Player player) {
        Robot robot = requireRobot(player);
        if (robot == null) {
            return;
        }
        RobotLevels levels = plugin.getRobotService().getLevels();
        int target = robot.getLevel() + 1;
        UpgradeResult result = levels.checkUpgrade(robot.getLevel(), target);
        if (result != UpgradeResult.OK) {
            switch (result) {
                case ALREADY_MAX -> plugin.getMessageManager().send(player, "robot.level-maxed",
                        MessageManager.placeholders("max", String.valueOf(levels.getMaxLevel())));
                case OUT_OF_RANGE -> plugin.getMessageManager().send(player, "robot.level-out-of-range",
                        MessageManager.placeholders("max", String.valueOf(levels.getMaxLevel())));
                default -> plugin.getMessageManager().send(player, "robot.level-not-higher");
            }
            build(player);
            return;
        }

        double cost = levels.upgradeCost(target);
        EconomyHook economy = plugin.getEconomyHook();
        boolean chargeRequired = cost > 0 && economy != null && economy.isEnabled();
        if (chargeRequired && !economy.has(player, cost)) {
            plugin.getMessageManager().send(player, "robot.not-enough-money",
                    MessageManager.placeholders("cost", economy.format(cost)));
            return;
        }
        if (chargeRequired) {
            economy.withdraw(player, cost);
        }
        robot.setLevel(target);
        plugin.getRobotService().saveAsync(robot);
        plugin.getRobotService().refreshStandName(robot);
        plugin.getMessageManager().send(player, "robot.upgraded",
                MessageManager.placeholders(
                        "level", String.valueOf(target),
                        "interval", String.valueOf(levels.intervalTicks(target)),
                        "range", String.valueOf(levels.range(target))));
        build(player);
    }

    private void toggleActive(Player player) {
        Robot robot = requireRobot(player);
        if (robot == null) {
            return;
        }
        if (robot.isActive()) {
            robot.setActive(false);
            plugin.getRobotService().saveAsync(robot);
            plugin.getMessageManager().send(player, "robot.stopped");
        } else {
            if (!robot.hasChest()) {
                plugin.getMessageManager().send(player, "robot.error-no-chest");
                return;
            }
            robot.setActive(true);
            robot.setChestFullNotified(false);
            plugin.getRobotService().saveAsync(robot);
            plugin.getMessageManager().send(player, "robot.started");
        }
        build(player);
    }
}
