package com.strawserver.strawskyblock.robot;

import java.util.UUID;

/**
 * 小機器人的記憶體模型，由 {@link RobotService} 持有，並對應資料庫 straw_skyblock_robots 一列。
 *
 * <p>每座島嶼以 {@code islandUuid} 作為鍵；機器人是虛擬的（不放置實體方塊 / 實體），
 * 以 {@code origin} 座標作為掃描中心，將挖到的掉落物存入 {@code chest} 連結的箱子。</p>
 */
public class Robot {

    private final UUID islandUuid;
    private UUID ownerUuid;
    private final String worldName;

    private int originX;
    private int originY;
    private int originZ;

    // 連結箱子座標；尚未設定時為 null。
    private Integer chestX;
    private Integer chestY;
    private Integer chestZ;

    private int speedLevel;
    private int lengthLevel;
    private boolean active;

    // ---- 執行期暫態（不需持久化）----
    private long tickCounter;
    private boolean chestFullNotified;

    public Robot(UUID islandUuid, UUID ownerUuid, String worldName,
                 int originX, int originY, int originZ,
                 Integer chestX, Integer chestY, Integer chestZ,
                 int speedLevel, int lengthLevel, boolean active) {
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
        this.speedLevel = speedLevel;
        this.lengthLevel = lengthLevel;
        this.active = active;
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

    public int getSpeedLevel() {
        return speedLevel;
    }

    public void setSpeedLevel(int speedLevel) {
        this.speedLevel = speedLevel;
    }

    public int getLengthLevel() {
        return lengthLevel;
    }

    public void setLengthLevel(int lengthLevel) {
        this.lengthLevel = lengthLevel;
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
