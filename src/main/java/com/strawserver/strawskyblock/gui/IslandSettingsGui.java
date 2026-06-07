package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.island.IslandFlag;
import com.strawserver.strawskyblock.island.IslandRole;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 島嶼設定 GUI，切換各項 Flag。
 */
public class IslandSettingsGui extends Gui {

    private static final int BACK_SLOT = 49;
    private final Island island;
    private final Map<Integer, IslandFlag> slotFlag = new HashMap<>();

    public IslandSettingsGui(StrawSkyBlockPlugin plugin, Island island) {
        super(plugin, 54, MiniMessageUtil.parse("<yellow>島嶼設定"));
        this.island = island;
    }

    @Override
    protected void build(Player player) {
        slotFlag.clear();
        inventory.clear();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        IslandFlag[] flags = IslandFlag.values();
        for (int i = 0; i < flags.length && i < slots.length; i++) {
            IslandFlag flag = flags[i];
            boolean value = island.getFlag(flag);
            ItemStack item = new ItemBuilder(value ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((value ? "<green>" : "<red>") + flag.getDisplayName())
                    .lore("<gray>目前狀態：" + (value ? "<green>開啟" : "<red>關閉"),
                            "<yellow>點擊切換")
                    .glow(value)
                    .build();
            set(slots[i], item);
            slotFlag.put(slots[i], flag);
        }

        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回主選單").build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            new IslandMainGui(plugin).open(player);
            return;
        }

        IslandFlag flag = slotFlag.get(slot);
        if (flag == null) {
            return;
        }
        if (!island.getRole(player.getUniqueId()).atLeast(IslandRole.ADMIN)) {
            plugin.getMessageManager().send(player, "common.no-permission");
            return;
        }
        boolean newValue = !island.getFlag(flag);
        plugin.getIslandService().setFlag(island, flag, newValue);
        build(player);
    }
}
