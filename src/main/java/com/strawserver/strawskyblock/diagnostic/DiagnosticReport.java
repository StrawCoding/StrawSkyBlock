package com.strawserver.strawskyblock.diagnostic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 單筆「錯誤診斷」報告，為不依賴 Bukkit 的純資料 + 格式化邏輯，方便單元測試。
 *
 * <p>設計目標：當 StrawSkyBlock 控制的流程（重生回家延遲傳送、顯式島嶼傳送、
 * 建立／造訪／重置／管理員傳送等）失敗時，能在大量的 Paper 日誌中以固定前綴
 * {@link #TAG} 印出「一小塊」可直接定位問題的資訊區塊，而不是被淹沒在巨量日誌裡。</p>
 *
 * <p>安全性：所有可能含有敏感資訊的字串（失敗原因、例外訊息、堆疊）都會經過
 * {@link #sanitize(String)} 過濾，避免外洩資料庫密碼或連線字串。</p>
 */
public final class DiagnosticReport {

    /** 統一前綴，方便管理員以關鍵字「錯誤診斷」在 console 搜尋。 */
    public static final String TAG = "[StrawSkyBlock][錯誤診斷]";

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 預設保留的堆疊行數（過多會再次淹沒日誌）。 */
    public static final int DEFAULT_STACK_FRAMES = 8;

    // 敏感資訊過濾：密碼鍵值與 JDBC 連線字串。
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?i)(pass(word)?|pwd)(\\s*[=:]\\s*)[^\\s,;&\"']+");
    private static final Pattern JDBC_PATTERN =
            Pattern.compile("(?i)jdbc:[^\\s\"']+");

    private final LocalDateTime timestamp;
    private final String operation;
    private final String playerName;
    private final String playerUuid;
    private final String sourceLocation;
    private final String destinationLocation;
    private final String islandId;
    private final String islandOwner;
    private final String reason;
    private final List<String> stackSnippet;

    private DiagnosticReport(Builder builder) {
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.operation = blankToNull(builder.operation);
        this.playerName = blankToNull(builder.playerName);
        this.playerUuid = blankToNull(builder.playerUuid);
        this.sourceLocation = blankToNull(builder.sourceLocation);
        this.destinationLocation = blankToNull(builder.destinationLocation);
        this.islandId = blankToNull(builder.islandId);
        this.islandOwner = blankToNull(builder.islandOwner);
        this.reason = builder.reason != null ? sanitize(builder.reason) : null;
        this.stackSnippet = Collections.unmodifiableList(new ArrayList<>(builder.stackSnippet));
    }

    public static Builder builder(String operation) {
        return new Builder(operation);
    }

    public String getOperation() {
        return operation;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public List<String> getStackSnippet() {
        return stackSnippet;
    }

    /**
     * 將報告格式化為多行字串；每一行都帶有 {@link #TAG} 前綴，確保即使 console
     * 只逐行顯示，管理員仍可用關鍵字搜尋到完整區塊。
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        List<String> lines = toLines();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(TAG).append(' ').append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * 報告內容（不含 {@link #TAG} 前綴）。用於寫入插件本地錯誤檔。
     */
    public List<String> toLines() {
        List<String> lines = new ArrayList<>();
        lines.add("===== 操作失敗診斷 @ " + timestamp.format(TIMESTAMP_FORMAT) + " =====");
        lines.add("操作: " + orUnknown(operation));
        if (playerName != null || playerUuid != null) {
            lines.add("玩家: " + orUnknown(playerName) + " (" + orUnknown(playerUuid) + ")");
        }
        if (sourceLocation != null) {
            lines.add("來源座標: " + sourceLocation);
        }
        if (destinationLocation != null) {
            lines.add("目的地座標: " + destinationLocation);
        }
        if (islandId != null || islandOwner != null) {
            lines.add("島嶼: " + orUnknown(islandId) + "（島主: " + orUnknown(islandOwner) + "）");
        }
        if (reason != null) {
            lines.add("失敗原因: " + reason);
        }
        if (!stackSnippet.isEmpty()) {
            lines.add("例外堆疊:");
            lines.addAll(stackSnippet);
        }
        lines.add("===========================================");
        return lines;
    }

    /**
     * 單行摘要，供管理員指令／聊天提示使用（已過濾敏感資訊）。
     */
    public String oneLineSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(timestamp.format(TIMESTAMP_FORMAT)).append("] ");
        sb.append(orUnknown(operation));
        if (playerName != null) {
            sb.append(" / 玩家=").append(playerName);
        }
        if (reason != null) {
            sb.append(" / 原因=").append(reason);
        } else if (!stackSnippet.isEmpty()) {
            sb.append(" / ").append(stackSnippet.get(0));
        }
        return sb.toString();
    }

    /**
     * 由 {@link Throwable} 萃取「精簡」堆疊片段：例外類別+訊息、有限的堆疊行，
     * 並追蹤根因（Caused by）。所有字串皆已過濾敏感資訊。
     */
    public static List<String> buildStackSnippet(Throwable throwable, int maxFrames) {
        List<String> lines = new ArrayList<>();
        if (throwable == null) {
            return lines;
        }
        int frames = Math.max(1, maxFrames);
        appendThrowable(lines, throwable, frames, false);
        Throwable cause = throwable.getCause();
        // 僅追蹤到根因，避免無限長鏈淹沒日誌。
        Throwable root = rootCause(throwable);
        if (cause != null && root != throwable) {
            appendThrowable(lines, root, frames, true);
        }
        return lines;
    }

    private static void appendThrowable(List<String> lines, Throwable t, int frames, boolean causedBy) {
        String header = (causedBy ? "Caused by: " : "")
                + t.getClass().getName() + ": " + String.valueOf(t.getMessage());
        lines.add(sanitize(header));
        StackTraceElement[] trace = t.getStackTrace();
        int limit = Math.min(frames, trace.length);
        for (int i = 0; i < limit; i++) {
            lines.add("    at " + sanitize(trace[i].toString()));
        }
        if (trace.length > limit) {
            lines.add("    ... 其餘 " + (trace.length - limit) + " 行堆疊省略");
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        int guard = 0;
        while (current.getCause() != null && current.getCause() != current && guard < 32) {
            current = current.getCause();
            guard++;
        }
        return current;
    }

    /**
     * 過濾可能外洩的敏感資訊（資料庫密碼、JDBC 連線字串）。
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        String out = PASSWORD_PATTERN.matcher(input).replaceAll("$1$3***");
        out = JDBC_PATTERN.matcher(out).replaceAll("jdbc:***");
        return out;
    }

    private static String orUnknown(String value) {
        return value == null ? "未知" : value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 報告建構器。
     */
    public static final class Builder {
        private LocalDateTime timestamp;
        private final String operation;
        private String playerName;
        private String playerUuid;
        private String sourceLocation;
        private String destinationLocation;
        private String islandId;
        private String islandOwner;
        private String reason;
        private List<String> stackSnippet = new ArrayList<>();

        private Builder(String operation) {
            this.operation = operation;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder player(String name, String uuid) {
            this.playerName = name;
            this.playerUuid = uuid;
            return this;
        }

        public Builder source(String location) {
            this.sourceLocation = location;
            return this;
        }

        public Builder destination(String location) {
            this.destinationLocation = location;
            return this;
        }

        public Builder island(String id, String owner) {
            this.islandId = id;
            this.islandOwner = owner;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder exception(Throwable throwable, int maxFrames) {
            this.stackSnippet = buildStackSnippet(throwable, maxFrames);
            return this;
        }

        public Builder stackSnippet(List<String> snippet) {
            this.stackSnippet = snippet != null ? new ArrayList<>(snippet) : new ArrayList<>();
            return this;
        }

        public DiagnosticReport build() {
            return new DiagnosticReport(this);
        }
    }
}
