package com.kammoun.raven.time;

import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Elects a single time authority across the survival network via a short-lived Redis lock
 * ({@code raven:time:authority}). Whoever holds the key drives the shared clock; the TTL means
 * that if the authority crashes the key expires and another server claims it within seconds.
 * {@link #heartbeat()} does blocking Redis I/O and is meant to be called off the main thread.
 */
public class TimeAuthority {

    private static final String KEY = "raven:time:authority";

    private final JavaPlugin plugin;
    private final JedisPool pool;
    private final String serverId;
    private final int ttlSeconds;
    private final AtomicBoolean authority = new AtomicBoolean(false);

    public TimeAuthority(JavaPlugin plugin, JedisPool pool, String serverId, int ttlSeconds) {
        this.plugin = plugin;
        this.pool = pool;
        this.serverId = serverId;
        this.ttlSeconds = ttlSeconds;
    }

    /** Acquires the lock if it's free, renews it if we already hold it, otherwise stands down. */
    public void heartbeat() {
        try (Jedis jedis = pool.getResource()) {
            String holder = jedis.get(KEY);
            if (serverId.equals(holder)) {
                // We own it — renew the TTL (XX: only if it still exists).
                authority.set("OK".equals(jedis.set(KEY, serverId, SetParams.setParams().xx().ex(ttlSeconds))));
            } else if (holder == null) {
                // Vacant — try to claim it atomically (NX: only if absent).
                authority.set("OK".equals(jedis.set(KEY, serverId, SetParams.setParams().nx().ex(ttlSeconds))));
            } else {
                authority.set(false);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TimeSync] Authority heartbeat failed: " + e.getMessage());
            authority.set(false);
        }
    }

    public boolean isAuthority() {
        return authority.get();
    }

    /** Releases the lock, but only if we currently hold it — never steals it from another server. */
    public void release() {
        try (Jedis jedis = pool.getResource()) {
            if (serverId.equals(jedis.get(KEY))) {
                jedis.del(KEY);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TimeSync] Failed to release authority lock: " + e.getMessage());
        }
        authority.set(false);
    }
}
