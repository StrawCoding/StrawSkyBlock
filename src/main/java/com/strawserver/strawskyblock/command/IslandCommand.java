package com.strawserver.strawskyblock.command;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.gui.ConfirmGui;
import com.strawserver.strawskyblock.gui.IslandAnimalGui;
import com.strawserver.strawskyblock.gui.IslandGeneratorGui;
import com.strawserver.strawskyblock.gui.IslandMainGui;
import com.strawserver.strawskyblock.gui.IslandMemberGui;
import com.strawserver.strawskyblock.gui.IslandSettingsGui;
import com.strawserver.strawskyblock.island.Island;
import com.strawserver.strawskyblock.util.IslandTeleportHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /is 指令與子指令處理。
 */
public class IslandCommand implements CommandExecutor, TabCompleter {

    private static final List<String> USER_SUBS = Arrays.asList(
            "create", "home", "sethome", "invite", "accept", "deny", "kick", "leave",
            "delete", "reset", "members", "settings", "generator", "animals", "robot", "top", "visit", "admin");

    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "reload", "tp", "delete", "reset", "info", "setowner", "bypass", "debug");

    private static final List<String> ROBOT_SUBS = Arrays.asList(
            "create", "chest", "speed", "length", "start", "stop", "info", "remove");

    private final StrawSkyBlockPlugin plugin;

    public IslandCommand(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            handleAdmin(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "common.player-only");
            return true;
        }

        if (args.length == 0) {
            requirePerm(player, "strawskyblock.user.open", () -> new IslandMainGui(plugin).open(player));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> requirePerm(player, "strawskyblock.user.create",
                    () -> plugin.getIslandService().createIsland(player));
            case "home" -> requirePerm(player, "strawskyblock.user.home",
                    () -> plugin.getIslandService().teleportHome(player));
            case "sethome" -> requirePerm(player, "strawskyblock.user.sethome",
                    () -> plugin.getIslandService().setHome(player));
            case "invite" -> requirePerm(player, "strawskyblock.user.invite", () -> handleInvite(player, args));
            case "accept" -> requirePerm(player, "strawskyblock.user.accept",
                    () -> plugin.getIslandService().acceptInvite(player));
            case "deny" -> requirePerm(player, "strawskyblock.user.accept",
                    () -> plugin.getIslandService().denyInvite(player));
            case "kick" -> requirePerm(player, "strawskyblock.user.kick", () -> handleKick(player, args));
            case "leave" -> requirePerm(player, "strawskyblock.user.leave",
                    () -> plugin.getIslandService().leaveIsland(player));
            case "delete" -> requirePerm(player, "strawskyblock.user.delete", () -> openConfirm(player, false));
            case "reset" -> requirePerm(player, "strawskyblock.user.reset", () -> openConfirm(player, true));
            case "members" -> requirePerm(player, "strawskyblock.user.open", () -> openMembers(player));
            case "settings" -> requirePerm(player, "strawskyblock.user.settings", () -> openSettings(player));
            case "generator" -> requirePerm(player, "strawskyblock.user.generator",
                    () -> new IslandGeneratorGui(plugin).open(player));
            case "animals" -> requirePerm(player, "strawskyblock.user.animals",
                    () -> new IslandAnimalGui(plugin).open(player));
            case "robot" -> requirePerm(player, "strawskyblock.user.robot", () -> handleRobot(player, args));
            case "top" -> requirePerm(player, "strawskyblock.user.top", () -> handleTop(player));
            case "visit" -> requirePerm(player, "strawskyblock.user.visit", () -> handleVisit(player, args));
            default -> new IslandMainGui(plugin).open(player);
        }
        return true;
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().send(player, "members.invite-none");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessageManager().send(player, "common.player-not-found",
                    MessageManager.placeholders("player", args[1]));
            return;
        }
        plugin.getIslandService().invite(player, target);
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            return;
        }
        plugin.getIslandService().kickMember(player, args[1]);
    }

    private void handleVisit(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().send(player, "common.player-not-found",
                    MessageManager.placeholders("player", ""));
            return;
        }
        plugin.getIslandService().visit(player, args[1]);
    }

    private void handleTop(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<java.util.Map<String, Object>> top =
                        plugin.getIslandService().getRepository().topBy("generator_blocks_broken", 10);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageManager().get("admin.info-header"));
                    int rank = 1;
                    for (java.util.Map<String, Object> row : top) {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                rank + ". " + row.get("owner_name") + " - " + row.get("value")));
                        rank++;
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("查詢排行榜失敗：" + e.getMessage());
            }
        });
    }

    private void openConfirm(Player player, boolean reset) {
        Island island = plugin.getIslandService().getByOwner(player.getUniqueId());
        if (island == null) {
            plugin.getMessageManager().send(player, "island.no-island");
            return;
        }
        new ConfirmGui(plugin, island, reset).open(player);
    }

    private void openMembers(Player player) {
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        if (island == null) {
            plugin.getMessageManager().send(player, "island.no-island");
            return;
        }
        new IslandMemberGui(plugin, island).open(player);
    }

    private void openSettings(Player player) {
        Island island = plugin.getIslandService().getByPlayer(player.getUniqueId());
        if (island == null) {
            plugin.getMessageManager().send(player, "island.no-island");
            return;
        }
        new IslandSettingsGui(plugin, island).open(player);
    }

    // =========================================================================
    // 小機器人指令
    // =========================================================================
    private void handleRobot(Player player, String[] args) {
        if (!plugin.getConfigManager().isRobotEnabled()) {
            plugin.getMessageManager().send(player, "robot.disabled");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageManager().send(player, "robot.usage");
            return;
        }

        // 機器人以「玩家所站位置的島嶼」為操作對象，必須在空島世界且為受信任成員。
        Island island = plugin.getIslandService().getByLocation(player.getLocation());
        if (island == null) {
            plugin.getMessageManager().send(player, "robot.not-on-island");
            return;
        }
        if (!island.getRole(player.getUniqueId()).isTrusted()) {
            plugin.getMessageManager().send(player, "robot.not-trusted");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create" -> robotCreate(player, island);
            case "chest" -> robotChest(player, island);
            case "speed" -> robotUpgradeSpeed(player, island, args);
            case "length" -> robotUpgradeLength(player, island, args);
            case "start" -> robotStart(player, island);
            case "stop" -> robotStop(player, island);
            case "info" -> robotInfo(player, island);
            case "remove" -> robotRemove(player, island);
            default -> plugin.getMessageManager().send(player, "robot.usage");
        }
    }

    private com.strawserver.strawskyblock.robot.Robot requireRobot(Player player, Island island) {
        com.strawserver.strawskyblock.robot.Robot robot =
                plugin.getRobotService().getByIsland(island.getIslandUuid());
        if (robot == null) {
            plugin.getMessageManager().send(player, "robot.none");
        }
        return robot;
    }

    private void robotCreate(Player player, Island island) {
        if (plugin.getRobotService().getByIsland(island.getIslandUuid()) != null) {
            plugin.getMessageManager().send(player, "robot.already-exists");
            return;
        }
        int max = plugin.getConfigManager().getRobotMaxPerIsland();
        if (max > 0 && plugin.getRobotService().countByOwner(player.getUniqueId()) >= max) {
            plugin.getMessageManager().send(player, "robot.limit-reached",
                    MessageManager.placeholders("max", String.valueOf(max)));
            return;
        }
        org.bukkit.block.Block block = player.getLocation().getBlock();
        plugin.getRobotService().createRobot(island, player.getUniqueId(),
                block.getX(), block.getY(), block.getZ());
        plugin.getMessageManager().send(player, "robot.created",
                MessageManager.placeholders(
                        "x", String.valueOf(block.getX()),
                        "y", String.valueOf(block.getY()),
                        "z", String.valueOf(block.getZ())));
    }

    private void robotChest(Player player, Island island) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        org.bukkit.block.Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof org.bukkit.block.Container)) {
            plugin.getMessageManager().send(player, "robot.chest-not-container");
            return;
        }
        if (!island.contains(target.getLocation())) {
            plugin.getMessageManager().send(player, "robot.chest-outside");
            return;
        }
        robot.setChest(target.getX(), target.getY(), target.getZ());
        plugin.getRobotService().saveAsync(robot);
        plugin.getMessageManager().send(player, "robot.chest-set",
                MessageManager.placeholders(
                        "x", String.valueOf(target.getX()),
                        "y", String.valueOf(target.getY()),
                        "z", String.valueOf(target.getZ())));
    }

    private Integer parseLevelArg(Player player, String[] args) {
        if (args.length < 3) {
            plugin.getMessageManager().send(player, "robot.level-usage");
            return null;
        }
        try {
            return Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(player, "robot.level-usage");
            return null;
        }
    }

    private void robotUpgradeSpeed(Player player, Island island, String[] args) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        Integer target = parseLevelArg(player, args);
        if (target == null) {
            return;
        }
        var levels = plugin.getRobotService().getLevels();
        var result = levels.checkUpgrade(robot.getSpeedLevel(), target);
        if (!handleUpgradeResult(player, result, levels.getMaxLevel())) {
            return;
        }
        double cost = levels.speedUpgradeCost(target);
        if (!chargeCost(player, cost)) {
            return;
        }
        robot.setSpeedLevel(target);
        plugin.getRobotService().saveAsync(robot);
        plugin.getMessageManager().send(player, "robot.speed-upgraded",
                MessageManager.placeholders(
                        "level", String.valueOf(target),
                        "interval", String.valueOf(levels.intervalTicks(target))));
    }

    private void robotUpgradeLength(Player player, Island island, String[] args) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        Integer target = parseLevelArg(player, args);
        if (target == null) {
            return;
        }
        var levels = plugin.getRobotService().getLevels();
        var result = levels.checkUpgrade(robot.getLengthLevel(), target);
        if (!handleUpgradeResult(player, result, levels.getMaxLevel())) {
            return;
        }
        double cost = levels.lengthUpgradeCost(target);
        if (!chargeCost(player, cost)) {
            return;
        }
        robot.setLengthLevel(target);
        plugin.getRobotService().saveAsync(robot);
        plugin.getMessageManager().send(player, "robot.length-upgraded",
                MessageManager.placeholders(
                        "level", String.valueOf(target),
                        "range", String.valueOf(levels.range(target))));
    }

    /**
     * @return true 表示驗證通過可繼續升級。
     */
    private boolean handleUpgradeResult(Player player,
                                        com.strawserver.strawskyblock.robot.UpgradeResult result, int max) {
        switch (result) {
            case OK -> {
                return true;
            }
            case OUT_OF_RANGE -> plugin.getMessageManager().send(player, "robot.level-out-of-range",
                    MessageManager.placeholders("max", String.valueOf(max)));
            case NOT_HIGHER -> plugin.getMessageManager().send(player, "robot.level-not-higher");
            case ALREADY_MAX -> plugin.getMessageManager().send(player, "robot.level-maxed",
                    MessageManager.placeholders("max", String.valueOf(max)));
        }
        return false;
    }

    /**
     * 升級花費 hook：若經濟系統可用且花費 &gt; 0 則扣款，否則略過（保留為未來付費功能掛點）。
     *
     * @return true 表示可繼續（免費或扣款成功）。
     */
    private boolean chargeCost(Player player, double cost) {
        if (cost <= 0) {
            return true;
        }
        var economy = plugin.getEconomyHook();
        if (economy == null || !economy.isEnabled()) {
            // 未啟用經濟系統時，視為免費（花費僅為設定佔位）。
            return true;
        }
        if (!economy.has(player, cost)) {
            plugin.getMessageManager().send(player, "robot.not-enough-money",
                    MessageManager.placeholders("cost", economy.format(cost)));
            return false;
        }
        economy.withdraw(player, cost);
        return true;
    }

    private void robotStart(Player player, Island island) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        if (!robot.hasChest()) {
            plugin.getMessageManager().send(player, "robot.error-no-chest");
            return;
        }
        robot.setActive(true);
        robot.setChestFullNotified(false);
        plugin.getRobotService().saveAsync(robot);
        plugin.getMessageManager().send(player, "robot.started");
    }

    private void robotStop(Player player, Island island) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        robot.setActive(false);
        plugin.getRobotService().saveAsync(robot);
        plugin.getMessageManager().send(player, "robot.stopped");
    }

    private void robotInfo(Player player, Island island) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        var levels = plugin.getRobotService().getLevels();
        String chest = robot.hasChest()
                ? robot.getChestX() + ", " + robot.getChestY() + ", " + robot.getChestZ()
                : "未設定";
        player.sendMessage(plugin.getMessageManager().get("robot.info-header"));
        robotInfoLine(player, "原點", robot.getOriginX() + ", " + robot.getOriginY() + ", " + robot.getOriginZ());
        robotInfoLine(player, "箱子", chest);
        robotInfoLine(player, "速度等級", "L" + robot.getSpeedLevel() + " / L" + levels.getMaxLevel()
                + "（每 " + levels.intervalTicks(robot.getSpeedLevel()) + " tick 挖一格）");
        robotInfoLine(player, "範圍等級", "L" + robot.getLengthLevel() + " / L" + levels.getMaxLevel()
                + "（半徑 " + levels.range(robot.getLengthLevel()) + " 格）");
        robotInfoLine(player, "狀態", robot.isActive() ? "運作中" : "停止");
    }

    private void robotInfoLine(Player player, String key, String value) {
        player.sendMessage(plugin.getMessageManager().get("admin.info-line",
                MessageManager.placeholders("key", key, "value", value)));
    }

    private void robotRemove(Player player, Island island) {
        com.strawserver.strawskyblock.robot.Robot robot = requireRobot(player, island);
        if (robot == null) {
            return;
        }
        plugin.getRobotService().removeRobot(robot);
        plugin.getMessageManager().send(player, "robot.removed");
    }

    // =========================================================================
    // 管理員指令
    // =========================================================================
    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().get("common.reload-usage"));
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "reload" -> {
                if (noPerm(sender, "strawskyblock.admin.reload")) return;
                plugin.reloadAll();
                plugin.getMessageManager().send(sender, "admin.reloaded");
            }
            case "bypass" -> {
                if (noPerm(sender, "strawskyblock.admin.bypass")) return;
                if (!(sender instanceof Player player)) {
                    plugin.getMessageManager().send(sender, "common.player-only");
                    return;
                }
                boolean now = plugin.toggleBypass(player.getUniqueId());
                plugin.getMessageManager().send(player, now ? "admin.bypass-on" : "admin.bypass-off");
            }
            case "debug" -> {
                if (noPerm(sender, "strawskyblock.admin.debug")) return;
                boolean now = !plugin.getConfigManager().isDebug();
                plugin.getConfigManager().setDebug(now);
                plugin.getMessageManager().send(sender, now ? "admin.debug-on" : "admin.debug-off");
            }
            case "tp" -> {
                if (noPerm(sender, "strawskyblock.admin.tp")) return;
                handleAdminTp(sender, args);
            }
            case "delete" -> {
                if (noPerm(sender, "strawskyblock.admin.delete")) return;
                handleAdminDelete(sender, args);
            }
            case "reset" -> {
                if (noPerm(sender, "strawskyblock.admin.reset")) return;
                handleAdminReset(sender, args);
            }
            case "info" -> {
                if (noPerm(sender, "strawskyblock.admin.info")) return;
                handleAdminInfo(sender, args);
            }
            case "setowner" -> {
                if (noPerm(sender, "strawskyblock.admin.setowner")) return;
                handleAdminSetOwner(sender, args);
            }
            default -> sender.sendMessage(plugin.getMessageManager().get("common.reload-usage"));
        }
    }

    private Island resolveIslandByOwnerName(CommandSender sender, String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        Island island = plugin.getIslandService().getByOwner(offline.getUniqueId());
        if (island == null) {
            plugin.getMessageManager().send(sender, "visit.no-target-island",
                    MessageManager.placeholders("player", name));
        }
        return island;
    }

    private void handleAdminTp(CommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof Player player)) {
            return;
        }
        Island island = resolveIslandByOwnerName(sender, args[2]);
        if (island == null) {
            return;
        }
        if (island.getHomeLocation() != null) {
            IslandTeleportHelper.teleportPlayer(plugin, player, island.getHomeLocation(), null);
            plugin.getMessageManager().send(sender, "admin.tp-done",
                    MessageManager.placeholders("player", args[2]));
        }
    }

    private void handleAdminDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return;
        }
        Island island = resolveIslandByOwnerName(sender, args[2]);
        if (island == null) {
            return;
        }
        plugin.getIslandService().performDelete(island);
        plugin.getMessageManager().send(sender, "admin.deleted-other",
                MessageManager.placeholders("player", args[2]));
    }

    private void handleAdminReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return;
        }
        Island island = resolveIslandByOwnerName(sender, args[2]);
        if (island == null) {
            return;
        }
        plugin.getIslandService().performReset(island, () ->
                plugin.getMessageManager().send(sender, "admin.reset-other",
                        MessageManager.placeholders("player", args[2])));
    }

    private void handleAdminInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return;
        }
        Island island = resolveIslandByOwnerName(sender, args[2]);
        if (island == null) {
            return;
        }
        sender.sendMessage(plugin.getMessageManager().get("admin.info-header"));
        sendInfo(sender, "島嶼UUID", island.getIslandUuid().toString());
        sendInfo(sender, "島主", island.getOwnerName());
        sendInfo(sender, "中心", island.getCenterX() + ", " + island.getCenterY() + ", " + island.getCenterZ());
        sendInfo(sender, "成員數", String.valueOf(island.getMembers().size()));
        sendInfo(sender, "大小", String.valueOf(island.getSize()));
    }

    private void sendInfo(CommandSender sender, String key, String value) {
        sender.sendMessage(plugin.getMessageManager().get("admin.info-line",
                MessageManager.placeholders("key", key, "value", value)));
    }

    private void handleAdminSetOwner(CommandSender sender, String[] args) {
        if (args.length < 4) {
            return;
        }
        Island island = resolveIslandByOwnerName(sender, args[2]);
        if (island == null) {
            return;
        }
        OfflinePlayer newOwner = Bukkit.getOfflinePlayer(args[3]);
        UUID newOwnerUuid = newOwner.getUniqueId();
        String newOwnerName = newOwner.getName() != null ? newOwner.getName() : args[3];
        plugin.getIslandService().setOwner(island, newOwnerUuid, newOwnerName);
        plugin.getMessageManager().send(sender, "admin.setowner-done",
                MessageManager.placeholders("player", args[2], "newowner", newOwnerName));
    }

    // =========================================================================
    private void requirePerm(Player player, String permission, Runnable action) {
        if (!player.hasPermission(permission)) {
            plugin.getMessageManager().send(player, "common.no-permission");
            return;
        }
        action.run();
    }

    private boolean noPerm(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            plugin.getMessageManager().send(sender, "common.no-permission");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(USER_SUBS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(ADMIN_SUBS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("robot")) {
            return filter(ROBOT_SUBS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("robot")
                && (args[1].equalsIgnoreCase("speed") || args[1].equalsIgnoreCase("length"))) {
            return filter(Arrays.asList("1", "2", "3", "4", "5", "6"), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite")
                || args[0].equalsIgnoreCase("visit") || args[0].equalsIgnoreCase("kick"))) {
            return onlinePlayerNames(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            return onlinePlayerNames(args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.startsWith(lower)).collect(Collectors.toList());
    }

    private List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
