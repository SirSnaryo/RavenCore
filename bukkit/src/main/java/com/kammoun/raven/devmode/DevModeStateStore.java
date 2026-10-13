package com.kammoun.raven.devmode;

import com.kammoun.raven.RavenCore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

/**
 * Keeps Developer Mode on across a server transfer, so the HUD comes back on the destination server
 * instead of silently switching itself off mid-session.
 */
public final class DevModeStateStore {

    private static final String KEY_PREFIX = "raven:devmode:active:";
    private static final int TTL_SECONDS = 600;

    private final RavenCore plugin;
    private final JedisPool pool;

    public DevModeStateStore(RavenCore plugin, JedisPool pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    public void save(UUID uuid, boolean active) {
        if (pool == null) return;
        if (!active) {
            clear(uuid);
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + uuid, TTL_SECONDS, "1");
        } catch (Exception e) {
            plugin.getLogger().warning("[DevMode] Failed to store Developer Mode state for " + uuid
                    + ": " + e.getMessage());
        }
    }

    public boolean load(UUID uuid) {
        if (pool == null) return false;
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(KEY_PREFIX + uuid) != null;
        } catch (Exception e) {
            plugin.getLogger().warning("[DevMode] Failed to read Developer Mode state for " + uuid
                    + ": " + e.getMessage());
            return false;
        }
    }

    public void clear(UUID uuid) {
        if (pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(KEY_PREFIX + uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[DevMode] Failed to clear Developer Mode state for " + uuid
                    + ": " + e.getMessage());
        }
    }
}
