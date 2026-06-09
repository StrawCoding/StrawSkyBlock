package com.strawserver.strawskyblock.robot;

import java.util.UUID;

/**
 * 小機器人的記憶體模型，由 {@link RobotService} 持有，並對應資料庫 straw_skyblock_robots 一列。
 *
 * <p>每台機器人以放置的 {@code origin} 座標（世界 + x/y/z）作為唯一鍵，於該處生成盔甲架小人作為
 * 外觀，並將挖到的掉落物存入 {@code chest} 連結的箱子。同一座島可同時擁有多台機器人，
 * 上限依放置者的 LuckPerms 權限決定。每台機器人有單一等級（L1~maxLevel），各自獨立升級。</p>
 */
public class Robot {

    private final UUID islandUuid;
    private UUID ownerUuid;
    private final String worldName;

    private int originX;
    private int originY;
    private int originZ;
    // 盔甲架小人面向（yaw），放置時記錄玩家當前方向。
    private float yaw;

    // 連結箱子座標；尚未設定時為 null。
    private Integer chestX;
    private Integer chestY;
    private Integer chestZ;

    private int level;
    private boolean active;

    // ---- 執行期暫態（不需持久化）----
    private long tickCounter;
    private boolean chestFullNotified;

    public Robot(UUID islandUuid, UUID ownerUuid, String worldName,
                 int originX, int originY, int originZ,
                 Integer chestX, Integer chestY, Integer chestZ,
                 int level, boolean active, float yaw) {
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.yaw = yaw;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
        this.level = level;
        this.active = active;
    }

    /**
     * 由世界名與座標組出唯一鍵字串，用於記憶體索引與盔甲架 PDC 標記。
     */
    public static String locationKey(String worldName, int x, int y, int z) {
        return worldName + ";" + x + ";" + y + ";" + z;
    }

    public String locationKey() {
        return locationKey(worldName, originX, originY, originZ);
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public void setOrigin(int x, int y, int z) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public boolean hasChest() {
        return chestX != null && chestY != null && chestZ != null;
    }

    public Integer getChestX() {
        return chestX;
    }

    public Integer getChestY() {
        return chestY;
    }

    public Integer getChestZ() {
        return chestZ;
    }

    public void setChest(Integer x, Integer y, Integer z) {
        this.chestX = x;
        this.chestY = y;
        this.chestZ = z;
        this.chestFullNotified = false;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            this.tickCounter = 0L;
        }
    }

    public long getTickCounter() {
        return tickCounter;
    }

    public void addTicks(long ticks) {
        this.tickCounter += ticks;
    }

    public void resetTickCounter() {
        this.tickCounter = 0L;
    }

    public boolean isChestFullNotified() {
        return chestFullNotified;
    }

    public void setChestFullNotified(boolean chestFullNotified) {
        this.chestFullNotified = chestFullNotified;
    }
}
