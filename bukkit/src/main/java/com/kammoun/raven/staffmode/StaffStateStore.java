package com.kammoun.raven.staffmode;

import com.kammoun.raven.RavenCore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

/**
 * Carries staff mode and vanish across a server transfer, so switching servers neither drops the
 * state nor leaves the player toggled on the destination without their bars and hide rules applied.
 */
public final class StaffStateStore {

    private static final String KEY_PREFIX = "raven:staff:state:";
    private static final int TTL_SECONDS = 600;

    private final RavenCore plugin;
    private final JedisPool pool;

    public StaffStateStore(RavenCore plugin, JedisPool pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    public record State(boolean staffMode, VanishTier vanishTier) {
    }

    public void save(UUID uuid, boolean staffMode, VanishTier vanishTier) {
        if (pool == null) return;
        if (!staffMode && vanishTier == null) {
            clear(uuid);
            return;
        }
        String value = (staffMode ? "1" : "0") + "|" + (vanishTier == null ? "-" : vanishTier.name());
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + uuid, TTL_SECONDS, value);
        } catch (Exception e) {
            plugin.getLogger().warning("[StaffMode] Failed to store staff state for " + uuid + ": " + e.getMessage());
        }
    }

    public State load(UUID uuid) {
        if (pool == null) return null;
        String value;
        try (Jedis jedis = pool.getResource()) {
            value = jedis.get(KEY_PREFIX + uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[StaffMode] Failed to read staff state for " + uuid + ": " + e.getMessage());
            return null;
        }
        if (value == null) return null;

        String[] parts = value.split("\\|", 2);
        if (parts.length != 2) return null;
        VanishTier tier = null;
        if (!parts[1].equals("-")) {
            try {
                tier = VanishTier.valueOf(parts[1]);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return new State(parts[0].equals("1"), tier);
    }

    public void clear(UUID uuid) {
        if (pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_PREFIX + uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[StaffMode] Failed to clear staff state for " + uuid + ": " + e.getMessage());
        }
    }
}
