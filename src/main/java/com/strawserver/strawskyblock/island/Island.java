package com.strawserver.strawskyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代表一座空島的記憶體模型，由 IslandCache 持有。
 */
public class Island {

    private long id;
    private final UUID islandUuid;
    private UUID ownerUuid;
    private String ownerName;
    private final String worldName;
    private final int index;
    private final int centerX;
    private final int centerY;
    private final int centerZ;

    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;

    private final int size;

    private final Map<UUID, IslandMember> members = new ConcurrentHashMap<>();
    private final Map<IslandFlag, Boolean> flags = new EnumMap<>(IslandFlag.class);

    public Island(long id, UUID islandUuid, UUID ownerUuid, String ownerName, String worldName,
                  int index, int centerX, int centerY, int centerZ,
                  double homeX, double homeY, double homeZ, float homeYaw, float homePitch, int size) {
        this.id = id;
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.index = index;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.homePitch = homePitch;
        this.size = size;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getIndex() {
        return index;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getSize() {
        return size;
    }

    public double getHomeX() {
        return homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    public float getHomeYaw() {
        return homeYaw;
    }

    public float getHomePitch() {
        return homePitch;
    }

    public void setHome(Location location) {
        this.homeX = location.getX();
        this.homeY = location.getY();
        this.homeZ = location.getZ();
        this.homeYaw = location.getYaw();
        this.homePitch = location.getPitch();
    }

    public Location getHomeLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
    }

    public Location getCenterLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    public Map<UUID, IslandMember> getMembers() {
        return members;
    }

    public IslandMember getMember(UUID uuid) {
        return members.get(uuid);
    }

    public void addMember(IslandMember member) {
        members.put(member.getPlayerUuid(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public IslandRole getRole(UUID uuid) {
        if (uuid != null && uuid.equals(ownerUuid)) {
            return IslandRole.OWNER;
        }
        IslandMember member = members.get(uuid);
        return member == null ? IslandRole.VISITOR : member.getRole();
    }

    public boolean isMember(UUID uuid) {
        return getRole(uuid).isTrusted();
    }

    public Map<IslandFlag, Boolean> getFlags() {
        return flags;
    }

    public boolean getFlag(IslandFlag flag) {
        return flags.getOrDefault(flag, flag.getFallbackDefault());
    }

    public void setFlag(IslandFlag flag, boolean value) {
        flags.put(flag, value);
    }

    /**
     * 判斷座標是否位於本島的方形範圍內（以中心點 ± size/2）。
     */
    public boolean contains(int x, int z) {
        int half = size / 2;
        return x >= centerX - half && x <= centerX + half
                && z >= centerZ - half && z <= centerZ + half;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return contains(location.getBlockX(), location.getBlockZ());
    }
}
