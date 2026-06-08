# StrawSkyBlock

稻草伺服器專用 Minecraft 空島插件，目標平台 **Paper 1.21.11 / Java 21**，資料庫使用 **MySQL**。

本版本為 MVP（最小可行版本），實作技術文件 v0.1 第 26 節列出的核心功能。

## 已實作功能（MVP）

- `/is` GUI 主選單（稻草風格、MiniMessage）
- 建立 / 返回空島，每人預設 1 座（可由 `strawskyblock.limit.*` 權限擴充）
- 多世界虛空生成（`straw_skyblock_world`，網格座標分配）
- 內建程式化島嶼模板（草地、樹、箱子、水、岩漿、初始物資）
- 島嶼保護系統（破壞、放置、容器、按鈕、撿物、PvP、爆炸、火焰、生物破壞等 Flag）
- 島嶼設定 / 成員管理 / 礦物機率 / 動物生成 GUI
- 刷石機鵝卵石礦物掉落（依 `config.yml` 機率，預設總和 100%）
- 挖石 1% 機率生成動物（權重、冷卻、數量上限、安全位置判定）
- MySQL 儲存（islands / members / flags / invites / stats）
- 排行榜查詢 `/is top`、`/is admin reload` 等管理指令
- Vault 經濟與 PlaceholderAPI（軟相依，選用）

## 建置

```bash
mvn clean package
```

產物：`target/StrawSkyBlock-1.0.0.jar`

> HikariCP 與 MySQL Driver 透過 Paper 的 `libraries` 機制於執行時自動下載，無需 shade。

## 安裝

1. 將 jar 放入伺服器 `plugins/`。
2. 首次啟動後編輯 `plugins/StrawSkyBlock/config.yml` 設定 MySQL 連線資訊。
3. 確認 MySQL 資料庫已建立（資料表會自動初始化）。
4. 重新啟動伺服器或執行 `/is admin reload`。

## 設定重點

- `database.*`：MySQL 連線資訊。
- `world.*`：空島世界名稱、間距、大小、Y 高度。
- `generator.drops`：刷石機掉落機率（建議總和 100%，啟動時會檢查並警告）。
- `animal-spawn.*`：動物生成機率、冷卻與上限（設 `-1` / `0` 代表不限制）。
- `protection.default-flags`：新島嶼預設旗標。

## 變更紀錄

### v1.0.3
- 修正：建立空島後玩家卡在「載入地形」、伺服器端仍留在主世界的問題。
  - 根因：從主世界跨維度傳送至虛空空島世界時，未預載目標／世界出生區塊，且 `keepSpawnInMemory(false)` 使出生區塊卸載；`teleportAsync` 亦未處理失敗，導致客戶端維度切換卡住而實體未移動。
  - 治本：新增 `IslandTeleportHelper` 預載區塊並處理傳送結果；空島世界改為 `keepSpawnInMemory(true)`；建立／回家等傳送流程統一使用輔助類別。

### v1.0.2
- 調整：初始島嶼不再直接放置水源與岩漿源方塊，水與岩漿改以「水桶 / 岩漿桶」形式放入初始箱子（符合規格 6.2，由玩家自行佈置刷石機）。

### v1.0.1
- 修正：建立空島後玩家卡在「載入地形」、身體仍留在主世界的問題。
  - 根因：完全虛空的世界中，伺服器嘗試「搜尋安全出生點」會卡死，導致跨世界傳送無法完成。
  - 治本：`VoidChunkGenerator` 覆寫 `getFixedSpawnLocation` 提供固定出生點；`WorldManager` 於世界出生點鋪設基岩平台並以 `setSpawnLocation` 設定固定出生座標（同時套用於既有世界）。

## 測試紀錄

- `mvn clean package`：編譯與打包通過（v1.0.1）。
- 掉落機率分布測試（200 萬次抽樣）：各礦物實測值與文件 7.3 期望值誤差 < 0.3%，鑽石 ≈ 1.00%、遠古遺骸 ≈ 0.10%（對應測試案例 24.3）。

> 完整執行期測試（建立空島、保護、MySQL 斷線重連）需於 Paper 伺服器 + MySQL 環境進行。

## 開發備註

- 所有 MySQL 存取於非同步執行緒；所有 Bukkit / 世界操作回到主執行緒。
- 保護判定全程使用 `IslandCache`（網格 O(1) 定位），不每次查資料庫。
- 刷石機鵝卵石以記憶體快取記錄，每 5 分鐘清理逾 10 分鐘未挖掘者，重啟不保存。
- 島嶼採軟刪除（`deleted=TRUE`）並保留 `island_index`，避免座標被重用。
