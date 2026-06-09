package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.robot.Robot;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 機器人商城 GUI：
 * <ul>
 *   <li>上方：購買一台新機器人（放入背包，於島上右鍵地面部署）。</li>
 *   <li>下方：列出玩家已部署的所有機器人，點擊任一台進入該台的升級畫面（各自獨立升級）。</li>
 * </ul>
 */
public class RobotShopGui extends Gui {

    private static final int BUY_SLOT = 4;
    private static final int LIST_START = 9;
    private static final int LIST_END = 44;
    private static final int BACK_SLOT = 49;

    /** 本次繪製時，list 區各 slot 對應的機器人座標鍵。 */
    private final Map<Integer, String> slotToRobot = new HashMap<>();

    public RobotShopGui(StrawSkyBlockPlugin plugin) {
        super(plugin, 54, MiniMessageUtil.parse("<gold>機器人商城"));
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        slotToRobot.clear();
        ItemBuilder filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");
        for (int i = 0; i < inventory.getSize(); i++) {
            set(i, filler.build());
        }

        double cost = plugin.getConfigManager().getRobotPurchaseCost();
        EconomyHook economy = plugin.getEconomyHook();
        String costText = costText(cost, economy);
        int owned = plugin.getRobotService().countByOwner(player.getUniqueId());
        int limit = plugin.getRobotService().getRobotLimit(player);

        set(BUY_SLOT, new ItemBuilder(Material.PLAYER_HEAD)
                .name("<gold><bold>購買小機器人")
                .lore(
                        "<gray>自動挖掘範圍內的鵝卵石並存入箱子，",
                        "<gray>挖到的方塊可能掉落礦物。",
                        "",
                        "<gray>價格：<gold>" + costText,
                        "<gray>你已部署：<aqua>" + owned + " / " + limit,
                        "",
                        "<yellow>點擊購買，機器人會放入你的背包",
                        "<dark_gray>右鍵地面放置部署，右鍵小人連結箱子")
                .glow(true)
                .hideAttributes()
                .build());

        // 列出玩家的所有機器人。
        List<Robot> robots = plugin.getRobotService().listByOwner(player.getUniqueId());
        var levels = plugin.getRobotService().getLevels();
        int slot = LIST_START;
        for (Robot robot : robots) {
            if (slot > LIST_END) {
                break;
            }
            String status = robot.isActive() ? "<green>運作中"
                    : (robot.hasChest() ? "<yellow>已停止" : "<red>未綁定箱子");
            set(slot, new ItemBuilder(Material.NETHERITE_PICKAXE)
                    .name("<aqua>小機器人 <yellow>L" + robot.getLevel() + " / L" + levels.getMaxLevel())
                    .lore(
                            "<gray>位置：<white>" + robot.getOriginX() + ", "
                                    + robot.getOriginY() + ", " + robot.getOriginZ(),
                            "<gray>挖掘間隔：<white>" + levels.intervalTicks(robot.getLevel()) + " tick",
                            "<gray>掃描半徑：<white>" + levels.range(robot.getLevel()) + " 格",
                            "<gray>狀態：" + status,
                            "",
                            "<yellow>點擊查看 / 升級這台機器人")
                    .hideAttributes()
                    .build());
            slotToRobot.put(slot, robot.locationKey());
            slot++;
        }

        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回主選單").build());
    }

    private String costText(double cost, EconomyHook economy) {
        if (cost <= 0) {
            return "免費";
        }
        return economy != null ? economy.format(cost) : String.valueOf(cost);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            new IslandMainGui(plugin).open(player);
            return;
        }
        if (slot == BUY_SLOT) {
            attemptPurchase(player);
            return;
        }
        String robotKey = slotToRobot.get(slot);
        if (robotKey != null) {
            new RobotUpgradeGui(plugin, robotKey).open(player);
        }
    }

    private void attemptPurchase(Player player) {
        if (!plugin.getConfigManager().isRobotEnabled()) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "robot.disabled");
            return;
        }

        double cost = plugin.getConfigManager().getRobotPurchaseCost();
        EconomyHook economy = plugin.getEconomyHook();
        boolean chargeRequired = cost > 0 && economy != null && economy.isEnabled();
        if (chargeRequired && !economy.has(player, cost)) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "robot.purchase-not-enough",
                    MessageManager.placeholders("cost", economy.format(cost)));
            return;
        }
        if (chargeRequired) {
            economy.withdraw(player, cost);
        }
        plugin.getRobotService().giveRobotItem(player,
                plugin.getConfigManager().getRobotDefaultLevel());
        player.closeInventory();
        plugin.getMessageManager().send(player, "robot.item-received");
        plugin.getRobotService().sendHelp(player);
    }
}
