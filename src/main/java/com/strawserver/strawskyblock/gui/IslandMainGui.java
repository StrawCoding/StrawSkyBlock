package com.strawserver.strawskyblock.gui;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.util.ItemBuilder;
import com.strawserver.strawskyblock.util.MiniMessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * /is 主選單。
 */
public class IslandMainGui extends Gui {

    private static final int SLOT_HOME = 10;
    private static final int SLOT_CREATE = 12;
    private static final int SLOT_MEMBERS = 14;
    private static final int SLOT_SETTINGS = 16;
    private static final int SLOT_GENERATOR = 28;
    private static final int SLOT_ANIMALS = 30;
    private static final int SLOT_TOP = 32;
    private static final int SLOT_DELETE = 34;

    public IslandMainGui(StrawSkyBlockPlugin plugin) {
        super(plugin, 45, MiniMessageUtil.parse("<gradient:#D6A84F:#7CC66A>稻草空島</gradient>"));
    }

    @Override
    protected void build(Player player) {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("<gray>").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            set(i, filler);
        }

        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        boolean hasIsland = island != null;

        if (hasIsland) {
            set(SLOT_HOME, new ItemBuilder(Material.GRASS_BLOCK)
                    .name("<green>返回空島")
                    .lore("<gray>點擊傳送回你的空島").build());
            set(SLOT_CREATE, new ItemBuilder(Material.BARRIER)
                    .name("<red>你已經擁有一座空島").build());
        } else {
            set(SLOT_HOME, new ItemBuilder(Material.GRAY_DYE)
                    .name("<dark_gray>返回空島").lore("<gray>你還沒有空島").build());
            set(SLOT_CREATE, new ItemBuilder(Material.GRASS_BLOCK)
                    .name("<gold><bold>建立我的空島")
                    .lore("<gray>免費建立屬於你的空島！", "<yellow>點擊建立").glow(true).build());
        }

        set(SLOT_MEMBERS, new ItemBuilder(Material.PLAYER_HEAD)
                .name("<aqua>成員管理").lore("<gray>邀請、查看與管理成員").build());
        set(SLOT_SETTINGS, new ItemBuilder(Material.COMPARATOR)
                .name("<yellow>島嶼設定").lore("<gray>調整訪客權限與島嶼旗標").build());
        set(SLOT_GENERATOR, new ItemBuilder(Material.COBBLESTONE)
                .name("<white>礦物機率").lore("<gray>查看刷石機掉落機率").build());
        set(SLOT_ANIMALS, new ItemBuilder(Material.WHEAT)
                .name("<green>動物生成").lore("<gray>查看挖石生成動物機率").build());
        set(SLOT_TOP, new ItemBuilder(Material.GOLD_INGOT)
                .name("<gold>排行榜").lore("<gray>查看空島排行榜").build());
        set(SLOT_DELETE, new ItemBuilder(Material.TNT)
                .name("<red><bold>刪除 / 重置空島")
                .lore("<gray>左鍵：重置空島", "<gray>右鍵：刪除空島", "<dark_red>危險操作，需二次確認！").build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());

        switch (slot) {
            case SLOT_CREATE -> {
                if (island == null) {
                    player.closeInventory();
                    plugin.getIslandService().createIsland(player);
                }
            }
            case SLOT_HOME -> {
                if (island != null) {
                    player.closeInventory();
                    plugin.getIslandService().teleportHome(player);
                }
            }
            case SLOT_MEMBERS -> {
                if (island != null) {
                    new IslandMemberGui(plugin, island).open(player);
                }
            }
            case SLOT_SETTINGS -> {
                if (island != null) {
                    new IslandSettingsGui(plugin, island).open(player);
                }
            }
            case SLOT_GENERATOR -> new IslandGeneratorGui(plugin).open(player);
            case SLOT_ANIMALS -> new IslandAnimalGui(plugin).open(player);
            case SLOT_TOP -> player.closeInventory();
            case SLOT_DELETE -> {
                if (island != null) {
                    boolean reset = event.isLeftClick();
                    new ConfirmGui(plugin, island, reset).open(player);
                }
            }
            default -> {
            }
        }
    }
}
