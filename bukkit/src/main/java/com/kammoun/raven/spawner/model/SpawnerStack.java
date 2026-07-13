package com.kammoun.raven.spawner.model;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable state for a placed spawner block.
 * The storage limit scales with stack size: maxStorage * stackAmount.
 */
@Getter
@Setter
public class SpawnerStack {

    private final Location location;
    private final SpawnerType type;

    private UUID ownerUUID;
    private int stackAmount;
    private int storedXp;
    private final Map<Material, Integer> storedItems;

    private long lastSpawnTime;
    private UUID openedByUUID;

    private boolean dirty;

    public SpawnerStack(Location location, SpawnerType type, int stackAmount,
                        Map<Material, Integer> storedItems, int storedXp,
                        long lastSpawnTime, UUID ownerUUID) {
        this.location = location;
        this.type = type;
        this.stackAmount = stackAmount;
        this.storedItems = storedItems;
        this.storedXp = storedXp;
        this.lastSpawnTime = lastSpawnTime;
        this.ownerUUID = ownerUUID;
        this.dirty = false;
    }

    public static SpawnerStack fresh(Location location, SpawnerType type, int stackAmount, UUID ownerUUID) {
        return new SpawnerStack(location, type, stackAmount, new HashMap<>(), 0,
                System.currentTimeMillis(), ownerUUID);
    }

    // ─── Storage mutations ───────────────────────────────────────────────────

    public void addItems(Material material, int amount) {
        storedItems.merge(material, amount, Integer::sum);
        dirty = true;
    }

    public void removeItems(Material material, int amount) {
        int current = storedItems.getOrDefault(material, 0);
        int toRemove = Math.min(current, amount);
        if (toRemove <= 0) return;
        int remaining = current - toRemove;
        if (remaining == 0) storedItems.remove(material);
        else storedItems.put(material, remaining);
        dirty = true;
    }

    public int getItemAmount(Material material) {
        return storedItems.getOrDefault(material, 0);
    }

    public int getTotalItems() {
        return storedItems.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void clearStorage() {
        storedItems.clear();
        dirty = true;
    }

    // ─── XP ─────────────────────────────────────────────────────────────────

    public void addXp(int amount) {
        if (amount <= 0) return;
        storedXp += amount;
        dirty = true;
    }

    public void dropXp() {
        if (storedXp <= 0) return;
        ExperienceOrb orb = location.getWorld().spawn(location, ExperienceOrb.class);
        orb.setExperience(storedXp);
        storedXp = 0;
        dirty = true;
    }

    public void giveXp(Player player) {
        if (storedXp <= 0) return;
        player.giveExp(storedXp);
        storedXp = 0;
        dirty = true;
    }

    // ─── Spawn eligibility ───────────────────────────────────────────────────

    /**
     * Returns true if the storage has room for at least one more drop cycle.
     * The effective capacity scales with the stack count.
     */
    public boolean canSpawn() {
        return getTotalItems() < (long) type.getMaxStorage() * stackAmount;
    }

    /**
     * Returns storage fill percentage (0–100).
     */
    public int getStoragePercent() {
        long maxItems = (long) type.getMaxStorage() * stackAmount;
        if (maxItems <= 0) return 0;
        return (int) Math.min(100, (getTotalItems() * 100L) / maxItems);
    }

    // ─── Player lock ─────────────────────────────────────────────────────────

    public boolean isInUse() {
        if (openedByUUID == null) return false;
        Player p = Bukkit.getPlayer(openedByUUID);
        return p != null && p.isOnline();
    }

    public void clearOpenedBy() {
        this.openedByUUID = null;
    }

    // ─── Dirty tracking ──────────────────────────────────────────────────────

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }
}
