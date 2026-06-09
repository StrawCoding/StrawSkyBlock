package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.robot.Robot;
import com.strawserver.strawskyblock.robot.RobotPurchase;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 機器人商城 GUI：玩家可付費購買一台小機器人，建立於其所站位置。
 */
public class RobotShopGui extends Gui {

    private static final int BUY_SLOT = 13;
    private static final int BACK_SLOT = 22;

    public RobotShopGui(StrawSkyBlockPlugin plugin) {
        super(plugin, 27, MiniMessageUtil.parse("<gold>機器人商城"));
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        ItemBuilder filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");
        for (int i = 0; i < inventory.getSize(); i++) {
            set(i, filler.build());
        }

        double cost = plugin.getConfigManager().getRobotPurchaseCost();
        EconomyHook economy = plugin.getEconomyHook();
        String costText = costText(cost, economy);
        int owned = plugin.getRobotService().countByOwner(player.getUniqueId());
        int max = plugin.getConfigManager().getRobotMaxPerIsland();
        String limitText = max > 0 ? owned + " / " + max : String.valueOf(owned);

        set(BUY_SLOT, new ItemBuilder(Material.NETHERITE_PICKAXE)
                .name("<gold><bold>購買小機器人")
                .lore(
                        "<gray>自動挖掘範圍內的鵝卵石並存入箱子，",
                        "<gray>挖到的方塊可能掉落礦物。",
                        "",
                        "<gray>價格：<gold>" + costText,
                        "<gray>你已擁有：<aqua>" + limitText,
                        "",
                        "<yellow>站在自己的島上點擊即可購買",
                        "<dark_gray>購買後使用 /is robot chest 連結箱子")
                .glow(true)
                .hideAttributes()
                .build());
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
        }
    }

    private void attemptPurchase(Player player) {
        if (!plugin.getConfigManager().isRobotEnabled()) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "robot.disabled");
            return;
        }

        Island island = plugin.getIslandService().getByLocation(player.getLocation());
        if (island == null) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "robot.not-on-island");
            return;
        }
        if (!island.getRole(player.getUniqueId()).isTrusted()) {
            player.closeInventory();
            plugin.getMessageManager().send(player, "robot.not-trusted");
            return;
        }

        double cost = plugin.getConfigManager().getRobotPurchaseCost();
        EconomyHook economy = plugin.getEconomyHook();
        boolean chargeRequired = cost > 0 && economy != null && economy.isEnabled();
        boolean hasFunds = !chargeRequired || economy.has(player, cost);
        boolean hasRobotOnIsland = plugin.getRobotService().getByIsland(island.getIslandUuid()) != null;
        int owned = plugin.getRobotService().countByOwner(player.getUniqueId());
        int max = plugin.getConfigManager().getRobotMaxPerIsland();

        RobotPurchase.Result result =
                RobotPurchase.evaluate(hasRobotOnIsland, owned, max, chargeRequired, hasFunds);
        switch (result) {
            case ALREADY_EXISTS -> {
                player.closeInventory();
                plugin.getMessageManager().send(player, "robot.already-exists");
            }
            case LIMIT_REACHED -> {
                player.closeInventory();
                plugin.getMessageManager().send(player, "robot.limit-reached",
                        MessageManager.placeholders("max", String.valueOf(max)));
            }
            case NOT_ENOUGH_MONEY -> {
                player.closeInventory();
                plugin.getMessageManager().send(player, "robot.purchase-not-enough",
                        MessageManager.placeholders("cost", economy.format(cost)));
            }
            case OK -> {
                if (chargeRequired) {
                    economy.withdraw(player, cost);
                }
                Block block = player.getLocation().getBlock();
                plugin.getRobotService().createRobot(island, player.getUniqueId(),
                        block.getX(), block.getY(), block.getZ());
                player.closeInventory();
                plugin.getMessageManager().send(player, "robot.purchased",
                        MessageManager.placeholders(
                                "x", String.valueOf(block.getX()),
                                "y", String.valueOf(block.getY()),
                                "z", String.valueOf(block.getZ())));
            }
        }
    }
}
