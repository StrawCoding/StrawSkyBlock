package com.strawserver.strawskyblock.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驗證錯誤診斷報告的純格式化、堆疊截斷與敏感資訊過濾行為，
 * 對應驗收條件 1、2、5（不外洩機密）。
 */
class DiagnosticReportTest {

    @Test
    void formatContainsTagAndCoreContext() {
        DiagnosticReport report = DiagnosticReport.builder("respawn-home-teleport")
                .player("Steve", "11111111-2222-3333-4444-555555555555")
                .source("straw_skyblock_world@0,100,0")
                .destination("straw_skyblock_world@1000,100,1000")
                .island("island-uuid-abc", "Alice")
                .reason("teleportAsync 回傳 false（傳送被拒絕）")
                .build();

        String formatted = report.format();
        // 每一行都應帶有統一前綴，方便在大量日誌中搜尋。
        for (String line : formatted.split("\\R")) {
            assertTrue(line.startsWith(DiagnosticReport.TAG),
                    "每行都需帶 TAG 前綴: " + line);
        }
        assertTrue(formatted.contains("respawn-home-teleport"));
        assertTrue(formatted.contains("Steve"));
        assertTrue(formatted.contains("11111111-2222-3333-4444-555555555555"));
        assertTrue(formatted.contains("straw_skyblock_world@0,100,0"));
        assertTrue(formatted.contains("straw_skyblock_world@1000,100,1000"));
        assertTrue(formatted.contains("island-uuid-abc"));
        assertTrue(formatted.contains("Alice"));
        assertTrue(formatted.contains("teleportAsync 回傳 false"));
    }

    @Test
    void sourceLocationIsOriginalPreTeleportNotDestination() {
        // v1.0.11 bug 回歸守門：island-home-teleport 成功後診斷的來源座標被誤標為目的地。
        // 修正後，診斷必須保留「傳送前」的原始來源座標，與目的地明確不同。
        String originalSource = "world@-9,63,23";
        String destination = "straw_skyblock_world@0,103,0";
        DiagnosticReport report = DiagnosticReport.builder("island-home-teleport")
                .player("xuczxc100", "11111111-2222-3333-4444-555555555555")
                .source(originalSource)
                .destination(destination)
                .reason("傳送回報成功，但成功後驗證偵測到可疑狀態")
                .build();

        List<String> lines = report.toLines();
        String sourceLine = lines.stream()
                .filter(l -> l.startsWith("來源座標:"))
                .findFirst()
                .orElseThrow();
        assertTrue(sourceLine.contains(originalSource), "來源座標必須為傳送前的原始位置");
        assertFalse(sourceLine.contains(destination), "來源座標不可被誤標為目的地");
        assertTrue(lines.stream().anyMatch(l -> l.equals("目的地座標: " + destination)));
    }

    @Test
    void optionalFieldsAreOmittedWhenAbsent() {
        DiagnosticReport report = DiagnosticReport.builder("island-home-teleport")
                .reason("目的地無效")
                .build();
        List<String> lines = report.toLines();
        assertTrue(lines.stream().noneMatch(l -> l.startsWith("目的地座標:")));
        assertTrue(lines.stream().noneMatch(l -> l.startsWith("島嶼:")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("失敗原因:")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("island-home-teleport")));
    }

    @Test
    void exceptionSnippetIncludesClassMessageAndRootCause() {
        Throwable root = new IllegalStateException("世界尚未載入");
        Throwable wrapper = new RuntimeException("傳送失敗", root);

        List<String> snippet = DiagnosticReport.buildStackSnippet(wrapper, 8);
        assertTrue(snippet.get(0).contains("java.lang.RuntimeException: 傳送失敗"));
        assertTrue(snippet.stream().anyMatch(
                l -> l.contains("Caused by: java.lang.IllegalStateException: 世界尚未載入")));
        assertTrue(snippet.stream().anyMatch(l -> l.trim().startsWith("at ")));
    }

    @Test
    void stackSnippetIsTruncatedToRequestedFrames() {
        Throwable t = deepException(50);
        int maxFrames = 4;
        List<String> snippet = DiagnosticReport.buildStackSnippet(t, maxFrames);
        long atLines = snippet.stream().filter(l -> l.trim().startsWith("at ")).count();
        // 至多 maxFrames 行堆疊（無 cause 時）。
        assertTrue(atLines <= maxFrames, "堆疊行數不應超過上限: " + atLines);
        assertTrue(snippet.stream().anyMatch(l -> l.contains("行堆疊省略")),
                "超出上限時應顯示省略提示");
    }

    @Test
    void sanitizeRedactsDatabasePasswordAndJdbcUrl() {
        String leaky = "Failed: jdbc:mysql://10.0.0.1:3306/straw_skyblock?password=SuperSecret123&useSSL=false";
        String cleaned = DiagnosticReport.sanitize(leaky);
        assertFalse(cleaned.contains("SuperSecret123"), "密碼不應外洩");
        assertFalse(cleaned.contains("jdbc:mysql://10.0.0.1"), "JDBC 連線字串不應外洩");
        assertTrue(cleaned.contains("***"));
    }

    @Test
    void sanitizeHandlesPwdAndColonForms() {
        assertFalse(DiagnosticReport.sanitize("pwd: hunter2").contains("hunter2"));
        assertFalse(DiagnosticReport.sanitize("password=topsecret").contains("topsecret"));
    }

    @Test
    void reasonIsSanitizedWhenBuildingReport() {
        DiagnosticReport report = DiagnosticReport.builder("island-create-teleport")
                .reason("DB error: password=leak123")
                .build();
        assertFalse(report.format().contains("leak123"));
    }

    @Test
    void oneLineSummaryStaysCompact() {
        DiagnosticReport report = DiagnosticReport.builder("island-visit-teleport")
                .player("Bob", "uuid")
                .reason("目的地無效")
                .build();
        String summary = report.oneLineSummary();
        assertTrue(summary.contains("island-visit-teleport"));
        assertTrue(summary.contains("Bob"));
        assertTrue(summary.contains("目的地無效"));
        assertFalse(summary.contains(System.lineSeparator()), "摘要必須為單行");
    }

    @Test
    void nullThrowableProducesEmptySnippet() {
        assertEquals(0, DiagnosticReport.buildStackSnippet(null, 8).size());
    }

    private static Throwable deepException(int depth) {
        try {
            recurse(depth);
        } catch (RuntimeException e) {
            return e;
        }
        return new RuntimeException("unreachable");
    }

    private static void recurse(int depth) {
        if (depth <= 0) {
            throw new RuntimeException("deep");
        }
        recurse(depth - 1);
    }
}
