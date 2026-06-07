package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.island.IslandRole;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 危險操作二次確認 GUI（刪除 / 重置）。
 */
public class ConfirmGui extends Gui {

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final Island island;
    private final boolean reset;

    public ConfirmGui(StrawSkyBlockPlugin plugin, Island island, boolean reset) {
        super(plugin, 27, MiniMessageUtil.parse(reset ? "<red>確認重置空島" : "<dark_red>確認刪除空島"));
        this.island = island;
        this.reset = reset;
    }

    @Override
    protected void build(Player player) {
        inventory.clear();
        String action = reset ? "重置" : "刪除";
        set(13, new ItemBuilder(Material.PAPER)
                .name("<yellow>你確定要" + action + "空島嗎？")
                .lore("<dark_red>此操作無法復原！").build());
        set(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name("<green><bold>確認" + action).build());
        set(CANCEL_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name("<red><bold>取消").build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == CANCEL_SLOT) {
            new IslandMainGui(plugin).open(player);
            return;
        }
        if (slot == CONFIRM_SLOT) {
            if (!island.getRole(player.getUniqueId()).atLeast(IslandRole.OWNER)) {
                plugin.getMessageManager().send(player, "common.no-permission");
                player.closeInventory();
                return;
            }
            player.closeInventory();
            if (reset) {
                plugin.getIslandService().resetIsland(player);
            } else {
                plugin.getIslandService().deleteIsland(player);
            }
        }
    }
}
