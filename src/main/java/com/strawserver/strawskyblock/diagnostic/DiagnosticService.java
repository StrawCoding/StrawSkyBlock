package com.strawserver.strawskyblock.diagnostic;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import com.strawserver.strawskyblock.config.MessageManager;
import com.strawserver.strawskyblock.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 錯誤診斷服務：集中處理 StrawSkyBlock 控制流程失敗時的「錯誤區塊」輸出。
 *
 * <ul>
 *   <li>於 console 以 {@link DiagnosticReport#TAG} 前綴印出精簡可定位的錯誤區塊；</li>
 *   <li>保留有界（bounded）的最近錯誤記錄，供 {@code /is admin diag} 查詢；</li>
 *   <li>可選擇寫入插件本地錯誤檔（data folder/{@value #ERROR_FILE_NAME}）；</li>
 *   <li>可選擇向線上管理員發送一則繁體中文提示，引導他們查看診斷區塊。</li>
 * </ul>
 *
 * <p>不會輸出正常成功路徑；僅在失敗時呼叫。所有文字皆經敏感資訊過濾。</p>
 */
public class DiagnosticService {

    public static final String ERROR_FILE_NAME = "error-log.txt";
    public static final String NOTIFY_PERMISSION = "strawskyblock.admin.diag";

    private final StrawSkyBlockPlugin plugin;
    private final Deque<DiagnosticReport> recent = new ArrayDeque<>();

    public DiagnosticService(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private int maxRecords() {
        return Math.max(1, plugin.getConfigManager().getDiagnosticsMaxRecords());
    }

    private int stackFrames() {
        return Math.max(1, plugin.getConfigManager().getDiagnosticsStackFrames());
    }

    /**
     * 紀錄並輸出一筆診斷報告（console 區塊 + 記憶體記錄 + 可選檔案 + 可選管理員提示）。
     */
    public synchronized void report(DiagnosticReport report) {
        if (report == null) {
            return;
        }
        // 1) console：以 SEVERE 印出整塊，確保在大量日誌中明顯可見。
        plugin.getLogger().severe(System.lineSeparator() + report.format());

        // 2) 記憶體中保留最近 N 筆。
        recent.addLast(report);
        while (recent.size() > maxRecords()) {
            recent.removeFirst();
        }

        // 3) 寫入插件本地錯誤檔（可關閉）。
        if (plugin.getConfigManager().isDiagnosticsWriteFile()) {
            writeToFile(report);
        }

        // 4) 通知線上管理員（可關閉）。
        if (plugin.getConfigManager().isDiagnosticsNotifyAdmins()) {
            notifyAdmins(report);
        }
    }

    /**
     * 建立一個帶有 operation 名稱的報告建構器；呼叫端再補上玩家／座標／島嶼／例外。
     */
    public DiagnosticReport.Builder builder(String operation) {
        return DiagnosticReport.builder(operation);
    }

    /**
     * 傳送相關失敗的便捷入口：自動帶入玩家、來源/目的地座標、（可得時）島嶼資訊與例外。
     *
     * <p>注意：此多載以 {@code player.getLocation()} 當作來源座標。跨世界傳送「成功後」才偵測到
     * 可疑狀態時，玩家當下位置已是目的地，會導致診斷的來源座標誤標為目的地（v1.0.11 觀察到的 bug）。
     * 該情境請改用 {@link #reportTeleportFailure(String, Player, Location, Location, String, Throwable)}
     * 並傳入「傳送前」擷取的原始來源座標。</p>
     */
    public void reportTeleportFailure(String operation,
                                      @Nullable Player player,
                                      @Nullable Location destination,
                                      String reason,
                                      @Nullable Throwable throwable) {
        reportTeleportFailure(operation, player, null, destination, reason, throwable);
    }

    /**
     * 傳送相關失敗的便捷入口（可指定原始來源座標）。
     *
     * @param source 傳送前擷取的原始來源座標；為 {@code null} 時退回使用 {@code player.getLocation()}。
     *               跨世界傳送成功後的診斷必須帶入此值，否則來源會被誤標為目的地。
     */
    public void reportTeleportFailure(String operation,
                                      @Nullable Player player,
                                      @Nullable Location source,
                                      @Nullable Location destination,
                                      String reason,
                                      @Nullable Throwable throwable) {
        DiagnosticReport.Builder builder = builder(operation).reason(reason);
        if (player != null) {
            builder.player(player.getName(), uuidString(player.getUniqueId()));
            Location effectiveSource = source != null ? source : player.getLocation();
            builder.source(formatLoc(effectiveSource));
            Island island = safeIslandOf(player.getUniqueId());
            if (island != null) {
                builder.island(island.getIslandUuid().toString(), island.getOwnerName());
            }
        } else if (source != null) {
            builder.source(formatLoc(source));
        }
        if (destination != null) {
            builder.destination(formatLoc(destination));
        }
        if (throwable != null) {
            builder.exception(throwable, stackFrames());
        }
        report(builder.build());
    }

    /**
     * @return 最近的診斷報告（最新在最後），最多回傳 {@code limit} 筆。
     */
    public synchronized List<DiagnosticReport> recent(int limit) {
        List<DiagnosticReport> all = new ArrayList<>(recent);
        if (limit <= 0 || all.size() <= limit) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - limit, all.size()));
    }

    @Nullable
    public synchronized DiagnosticReport latest() {
        return recent.peekLast();
    }

    public Path errorFilePath() {
        return plugin.getDataFolder().toPath().resolve(ERROR_FILE_NAME);
    }

    private void writeToFile(DiagnosticReport report) {
        try {
            Path folder = plugin.getDataFolder().toPath();
            Files.createDirectories(folder);
            Path file = folder.resolve(ERROR_FILE_NAME);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String line : report.toLines()) {
                    writer.write(line);
                    writer.write(System.lineSeparator());
                }
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "無法寫入錯誤診斷檔: " + e.getMessage());
        }
    }

    private void notifyAdmins(DiagnosticReport report) {
        Runnable task = () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission(NOTIFY_PERMISSION)) {
                    plugin.getMessageManager().send(online, "diagnostics.admin-alert",
                            MessageManager.placeholders("operation", safe(report.getOperation())));
                }
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Nullable
    private Island safeIslandOf(UUID uuid) {
        try {
            if (plugin.getIslandService() == null) {
                return null;
            }
            return plugin.getIslandService().getByPlayer(uuid);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String uuidString(@Nullable UUID uuid) {
        return uuid == null ? "未知" : uuid.toString();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "未知" : value;
    }

    private static String formatLoc(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return "未知";
        }
        return location.getWorld().getName() + "@"
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
