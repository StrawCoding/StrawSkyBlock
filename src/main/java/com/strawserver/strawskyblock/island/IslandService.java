package com.strawserver.strawskyblock.island;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 島嶼業務邏輯總管：協調 Repository、Cache、Allocator、Template 與世界 / 傳送操作。
 * 規則：所有資料庫存取於非同步執行緒；所有 Bukkit / 世界操作回到主執行緒。
 */
public class IslandService {

    private final StrawSkyBlockPlugin plugin;
    private final IslandRepository repository;
    private final IslandCache cache;
    private final IslandLocationAllocator allocator;
    private final IslandTemplateService templateService;

    public IslandService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
        this.repository = new IslandRepository(plugin);
        this.cache = new IslandCache(plugin);
        this.allocator = new IslandLocationAllocator(plugin);
        this.templateService = new IslandTemplateService(plugin);
    }

    public IslandCache getCache() {
        return cache;
    }

    public IslandRepository getRepository() {
        return repository;
    }

    /**
     * 啟動時載入所有島嶼到快取並初始化座標分配器。
     */
    public void loadAll() {
        runAsync(() -> {
            try {
                List<Island> islands = repository.loadAllActiveIslands();
                int maxIndex = repository.findMaxIndex();
                runSync(() -> {
                    cache.clear();
                    for (Island island : islands) {
                        cache.add(island);
                    }
                    allocator.init(maxIndex);
                    plugin.getLogger().info("已載入 " + islands.size() + " 座島嶼到快取。");
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("載入島嶼失敗：" + e.getMessage());
            }
        });
    }

    public Island getByPlayer(UUID playerUuid) {
        return cache.getByMember(playerUuid);
    }

    public Island getByOwner(UUID ownerUuid) {
        return cache.getByOwner(ownerUuid);
    }

    public Island getByLocation(Location location) {
        return cache.getByLocation(location);
    }

    private Map<IslandFlag, Boolean> buildDefaultFlags() {
        Map<IslandFlag, Boolean> flags = new EnumMap<>(IslandFlag.class);
        for (IslandFlag flag : IslandFlag.values()) {
            flags.put(flag, plugin.getConfigManager().getDefaultFlag(flag.getKey(), flag.getFallbackDefault()));
        }
        return flags;
    }

    // =========================================================================
    // 建立空島
    // =========================================================================
    public void createIsland(Player player) {
        UUID uuid = player.getUniqueId();
        if (cache.getByOwner(uuid) != null) {
            msg(player, "island.already-have");
            return;
        }
        msg(player, "island.creating");

        final String name = player.getName();
        runAsync(() -> {
            try {
                if (repository.ownerHasIsland(uuid)) {
                    runSync(() -> msg(player, "island.already-have"));
                    return;
                }
                int index = allocator.allocateIndex();
                int[] center = allocator.computeCenter(index);
                int islandY = plugin.getConfigManager().getIslandY();
                int size = plugin.getConfigManager().getIslandSize();
                String worldName = plugin.getConfigManager().getIslandWorld();

                double homeX = center[0] + plugin.getConfigManager().getSpawnOffsetX() + 0.5;
                double homeY = islandY + plugin.getConfigManager().getSpawnOffsetY();
                double homeZ = center[1] + plugin.getConfigManager().getSpawnOffsetZ() + 0.5;

                UUID islandUuid = UUID.randomUUID();
                Island island = new Island(0, islandUuid, uuid, name, worldName, index,
                        center[0], islandY, center[1], homeX, homeY, homeZ, 0F, 0F, size);
                Map<IslandFlag, Boolean> defaultFlags = buildDefaultFlags();
                for (Map.Entry<IslandFlag, Boolean> e : defaultFlags.entrySet()) {
                    island.setFlag(e.getKey(), e.getValue());
                }
                island.addMember(new IslandMember(uuid, name, IslandRole.OWNER));

                repository.insertIsland(island, defaultFlags);

                runSync(() -> {
                    World world = plugin.getWorldManager().getIslandWorld();
                    if (world == null) {
                        msg(player, "island.create-failed");
                        return;
                    }
                    Location home = templateService.pasteDefaultIsland(world,
                            island.getCenterX(), island.getCenterY(), island.getCenterZ());
                    island.setHome(home);
                    cache.add(island);
                    if (player.isOnline()) {
                        player.teleportAsync(home);
                        msg(player, "island.created");
                    }
                    // home 已由模板計算，更新回資料庫（非阻塞）
                    runAsync(() -> {
                        try {
                            repository.updateHome(island);
                        } catch (SQLException ignored) {
                        }
                    });
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("建立島嶼失敗：" + e.getMessage());
                runSync(() -> msg(player, "island.create-failed"));
            }
        });
    }

    // =========================================================================
    // 傳送 / Home
    // =========================================================================
    public void teleportHome(Player player) {
        Island island = cache.getByMember(player.getUniqueId());
        if (island == null) {
            msg(player, "island.no-island");
            return;
        }
        Location home = island.getHomeLocation();
        if (home == null) {
            msg(player, "island.create-failed");
            return;
        }
        player.teleportAsync(home);
        msg(player, "island.teleport-home");
    }

    public void setHome(Player player) {
        Island island = cache.getByOwner(player.getUniqueId());
        if (island == null) {
            // 允許 ADMIN 角色設定
            island = cache.getByMember(player.getUniqueId());
            if (island == null || !island.getRole(player.getUniqueId()).atLeast(IslandRole.ADMIN)) {
                msg(player, "island.no-island");
                return;
            }
        }
        if (!island.contains(player.getLocation())) {
            msg(player, "island.set-home-outside");
            return;
        }
        island.setHome(player.getLocation());
        final Island target = island;
        runAsync(() -> {
            try {
                repository.updateHome(target);
            } catch (SQLException e) {
                plugin.getLogger().warning("更新 home 失敗：" + e.getMessage());
            }
        });
        msg(player, "island.set-home");
    }

    public void visit(Player player, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Island island = cache.getByOwner(target.getUniqueId());
        if (island == null) {
            msg(player, "visit.no-target-island", MessageManager.placeholders("player", targetName));
            return;
        }
        Location home = island.getHomeLocation();
        if (home != null) {
            player.teleportAsync(home);
            msg(player, "visit.teleported", MessageManager.placeholders("player", island.getOwnerName()));
        }
    }

    // =========================================================================
    // 刪除 / 重置
    // =========================================================================
    public void deleteIsland(Player player) {
        Island island = cache.getByOwner(player.getUniqueId());
        if (island == null) {
            msg(player, "island.no-island");
            return;
        }
        performDelete(island);
        msg(player, "island.deleted");
    }

    public void performDelete(Island island) {
        cache.remove(island);
        // 將島上玩家送回主世界
        World main = Bukkit.getWorlds().get(0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.contains(online.getLocation())) {
                online.teleportAsync(main.getSpawnLocation());
            }
        }
        runAsync(() -> {
            try {
                repository.softDeleteIsland(island.getIslandUuid());
            } catch (SQLException e) {
                plugin.getLogger().severe("刪除島嶼失敗：" + e.getMessage());
            }
        });
    }

    public void resetIsland(Player player) {
        Island island = cache.getByOwner(player.getUniqueId());
        if (island == null) {
            msg(player, "island.no-island");
            return;
        }
        performReset(island, () -> {
            if (player.isOnline()) {
                Location home = island.getHomeLocation();
                if (home != null) {
                    player.teleportAsync(home);
                }
                msg(player, "island.reset");
            }
        });
    }

    public void performReset(Island island, Runnable onComplete) {
        templateService.resetIsland(island, onComplete);
    }

    // =========================================================================
    // 成員系統
    // =========================================================================
    public void invite(Player owner, Player target) {
        Island island = cache.getByOwner(owner.getUniqueId());
        if (island == null || !island.getRole(owner.getUniqueId()).atLeast(IslandRole.ADMIN)) {
            msg(owner, "island.no-island");
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            msg(owner, "members.invite-self");
            return;
        }
        if (island.isMember(target.getUniqueId())) {
            msg(owner, "members.already-member",
                    MessageManager.placeholders("player", target.getName()));
            return;
        }
        long expires = System.currentTimeMillis() + 120_000L;
        runAsync(() -> {
            try {
                repository.insertInvite(island.getIslandUuid(), owner.getUniqueId(),
                        target.getUniqueId(), expires);
                runSync(() -> {
                    msg(owner, "members.invite-sent",
                            MessageManager.placeholders("player", target.getName()));
                    if (target.isOnline()) {
                        msg(target, "members.invite-received",
                                MessageManager.placeholders("player", owner.getName()));
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("建立邀請失敗：" + e.getMessage());
            }
        });
    }

    public void acceptInvite(Player player) {
        runAsync(() -> {
            try {
                String islandUuidStr = repository.findActiveInviteIsland(player.getUniqueId());
                if (islandUuidStr == null) {
                    runSync(() -> msg(player, "members.invite-none"));
                    return;
                }
                UUID islandUuid = UUID.fromString(islandUuidStr);
                Island island = cache.getByUuid(islandUuid);
                if (island == null) {
                    runSync(() -> msg(player, "members.invite-expired"));
                    repository.deleteInvites(player.getUniqueId());
                    return;
                }
                IslandMember member = new IslandMember(player.getUniqueId(), player.getName(), IslandRole.MEMBER);
                repository.addOrUpdateMember(islandUuid, member);
                repository.deleteInvites(player.getUniqueId());
                runSync(() -> {
                    island.addMember(member);
                    msg(player, "members.invite-accepted",
                            MessageManager.placeholders("player", island.getOwnerName()));
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("接受邀請失敗：" + e.getMessage());
            }
        });
    }

    public void denyInvite(Player player) {
        runAsync(() -> {
            try {
                String islandUuidStr = repository.findActiveInviteIsland(player.getUniqueId());
                repository.deleteInvites(player.getUniqueId());
                runSync(() -> msg(player, islandUuidStr == null ? "members.invite-none" : "members.invite-denied"));
            } catch (SQLException e) {
                plugin.getLogger().warning("拒絕邀請失敗：" + e.getMessage());
            }
        });
    }

    public void kickMember(Player owner, String targetName) {
        Island island = cache.getByOwner(owner.getUniqueId());
        if (island == null || !island.getRole(owner.getUniqueId()).atLeast(IslandRole.ADMIN)) {
            msg(owner, "island.no-island");
            return;
        }
        IslandMember target = null;
        for (IslandMember m : island.getMembers().values()) {
            if (m.getPlayerName().equalsIgnoreCase(targetName) && m.getRole() != IslandRole.OWNER) {
                target = m;
                break;
            }
        }
        if (target == null) {
            msg(owner, "members.kick-not-member",
                    MessageManager.placeholders("player", targetName));
            return;
        }
        final IslandMember finalTarget = target;
        island.removeMember(finalTarget.getPlayerUuid());
        runAsync(() -> {
            try {
                repository.removeMember(island.getIslandUuid(), finalTarget.getPlayerUuid());
            } catch (SQLException e) {
                plugin.getLogger().warning("踢出成員失敗：" + e.getMessage());
            }
        });
        msg(owner, "members.kicked", MessageManager.placeholders("player", finalTarget.getPlayerName()));
        Player onlineTarget = Bukkit.getPlayer(finalTarget.getPlayerUuid());
        if (onlineTarget != null) {
            msg(onlineTarget, "members.kicked-target");
        }
    }

    public void leaveIsland(Player player) {
        Island island = cache.getByMember(player.getUniqueId());
        if (island == null) {
            msg(player, "members.not-member");
            return;
        }
        if (island.getOwnerUuid().equals(player.getUniqueId())) {
            msg(player, "members.owner-cannot-leave");
            return;
        }
        island.removeMember(player.getUniqueId());
        runAsync(() -> {
            try {
                repository.removeMember(island.getIslandUuid(), player.getUniqueId());
            } catch (SQLException e) {
                plugin.getLogger().warning("離開島嶼失敗：" + e.getMessage());
            }
        });
        msg(player, "members.left");
    }

    public void addStats(Island island, long blocks, long generatorBroken, long ores, long animals) {
        runAsync(() -> {
            try {
                repository.incrementStats(island.getIslandUuid(), blocks, generatorBroken, ores, animals);
            } catch (SQLException e) {
                plugin.getLogger().warning("更新統計失敗：" + e.getMessage());
            }
        });
    }

    public void setFlag(Island island, IslandFlag flag, boolean value) {
        island.setFlag(flag, value);
        runAsync(() -> {
            try {
                repository.updateFlag(island.getIslandUuid(), flag, value);
            } catch (SQLException e) {
                plugin.getLogger().warning("更新 flag 失敗：" + e.getMessage());
            }
        });
    }

    public void setOwner(Island island, UUID newOwner, String newOwnerName) {
        UUID oldOwner = island.getOwnerUuid();
        island.setOwnerUuid(newOwner);
        island.setOwnerName(newOwnerName);
        island.addMember(new IslandMember(newOwner, newOwnerName, IslandRole.OWNER));
        IslandMember oldMember = island.getMember(oldOwner);
        if (oldMember != null) {
            oldMember.setRole(IslandRole.MEMBER);
        }
        cache.reindexOwner(oldOwner, island);
        runAsync(() -> {
            try {
                repository.updateOwner(island.getIslandUuid(), newOwner, newOwnerName);
                repository.addOrUpdateMember(island.getIslandUuid(),
                        new IslandMember(newOwner, newOwnerName, IslandRole.OWNER));
                if (oldMember != null) {
                    repository.addOrUpdateMember(island.getIslandUuid(),
                            new IslandMember(oldOwner, oldMember.getPlayerName(), IslandRole.MEMBER));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("轉移島主失敗：" + e.getMessage());
            }
        });
    }

    // =========================================================================
    // helper
    // =========================================================================
    private void msg(Player player, String key) {
        plugin.getMessageManager().send(player, key);
    }

    private void msg(Player player, String key, Map<String, String> placeholders) {
        plugin.getMessageManager().send(player, key, placeholders);
    }

    private void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }
}
