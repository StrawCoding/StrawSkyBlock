package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.economy.EconomyHook;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
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

        set(BUY_SLOT, new ItemBuilder(Material.PLAYER_HEAD)
                .name("<gold><bold>購買小機器人")
                .lore(
                        "<gray>自動挖掘範圍內的鵝卵石並存入箱子，",
                        "<gray>挖到的方塊可能掉落礦物。",
                        "",
                        "<gray>價格：<gold>" + costText,
                        "<gray>你已部署：<aqua>" + limitText,
                        "",
                        "<yellow>點擊購買，機器人會放入你的背包",
                        "<dark_gray>右鍵地面放置部署，右鍵小人連結箱子")
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
                plugin.getConfigManager().getRobotDefaultSpeedLevel(),
                plugin.getConfigManager().getRobotDefaultLengthLevel());
        player.closeInventory();
        plugin.getMessageManager().send(player, "robot.item-received");
        plugin.getRobotService().sendHelp(player);
    }
}
