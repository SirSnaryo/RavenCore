package com.kammoun.raven.spawner.manager;

import com.kammoun.raven.spawner.model.SpawnerStack;
import com.kammoun.raven.spawner.model.SpawnerType;
import org.bukkit.Location;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active {@link SpawnerStack} instances in memory.
 * Not a singleton — obtained via SpawnerModule.
 */
public class SpawnerStackManager {

    private final ConcurrentHashMap<String, SpawnerStack> stacks = new ConcurrentHashMap<>();

    public SpawnerStack get(Location loc) {
        return stacks.get(key(loc));
    }

    public boolean has(Location loc) {
        return stacks.containsKey(key(loc));
    }

    /** Adds a pre-built stack directly (used during DB load). */
    public void put(SpawnerStack stack) {
        stacks.put(key(stack.getLocation()), stack);
    }

    /**
     * Adds {@code amount} spawners to the location.
     * If a stack already exists there, increments its count; otherwise creates a fresh one.
     */
    public SpawnerStack addOrStack(Location loc, SpawnerType type, int amount, UUID owner) {
        SpawnerStack existing = get(loc);
        if (existing != null) {
            existing.setStackAmount(existing.getStackAmount() + amount);
            existing.markDirty();
            return existing;
        }
        SpawnerStack fresh = SpawnerStack.fresh(loc, type, amount, owner);
        stacks.put(key(loc), fresh);
        return fresh;
    }

    public void remove(Location loc) {
        stacks.remove(key(loc));
    }

    public Collection<SpawnerStack> all() {
        return stacks.values();
    }

    public void clear() {
        stacks.clear();
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
