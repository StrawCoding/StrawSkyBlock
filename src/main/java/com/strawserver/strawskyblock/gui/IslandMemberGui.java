package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.island.IslandMember;
import com.strawserver.strawskyblock.island.IslandRole;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成員管理 GUI：顯示成員列表，島主 / 副島主可點擊踢出。
 */
public class IslandMemberGui extends Gui {

    private static final int BACK_SLOT = 49;
    private final Island island;
    private final Map<Integer, IslandMember> slotMember = new HashMap<>();

    public IslandMemberGui(StrawSkyBlockPlugin plugin, Island island) {
        super(plugin, 54, MiniMessageUtil.parse("<aqua>成員管理"));
        this.island = island;
    }

    @Override
    protected void build(Player player) {
        slotMember.clear();
        inventory.clear();
        List<IslandMember> members = new ArrayList<>(island.getMembers().values());
        int slot = 0;
        for (IslandMember member : members) {
            if (slot >= 45) {
                break;
            }
            ItemStack head = buildHead(member);
            set(slot, head);
            slotMember.put(slot, member);
            slot++;
        }

        set(BACK_SLOT, new ItemBuilder(Material.ARROW).name("<gray>返回主選單").build());
        set(53, new ItemBuilder(Material.BOOK)
                .name("<yellow>邀請玩家")
                .lore("<gray>使用指令：<white>/is invite <玩家>").build());
    }

    private ItemStack buildHead(IslandMember member) {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                .name("<yellow>" + member.getPlayerName())
                .lore("<gray>角色：<white>" + member.getRole().getDisplayName(),
                        member.getRole() == IslandRole.OWNER ? "" : "<red>左鍵踢出");
        ItemStack item = builder.build();
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(member.getPlayerUuid()));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            new IslandMainGui(plugin).open(player);
            return;
        }

        IslandMember member = slotMember.get(slot);
        if (member == null || member.getRole() == IslandRole.OWNER) {
            return;
        }
        if (!island.getRole(player.getUniqueId()).atLeast(IslandRole.ADMIN)) {
            plugin.getMessageManager().send(player, "common.no-permission");
            return;
        }
        plugin.getIslandService().kickMember(player, member.getPlayerName());
        build(player);
    }
}
