package com.kammoun.raven.time;

import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeAuthority {

    private static final String KEY_PREFIX = "raven:time:authority:";

    private final JavaPlugin plugin;
    private final JedisPool pool;
    private final String serverId;
    private final String key;
    private final int ttlSeconds;
    private final AtomicBoolean authority = new AtomicBoolean(false);

    public TimeAuthority(JavaPlugin plugin, JedisPool pool, String serverId, int ttlSeconds) {
        this(plugin, pool, serverId, ttlSeconds, "survival");
    }

    public TimeAuthority(JavaPlugin plugin, JedisPool pool, String serverId, int ttlSeconds, String scope) {
        this.plugin = plugin;
        this.pool = pool;
        this.serverId = serverId;
        this.ttlSeconds = ttlSeconds;
        this.key = KEY_PREFIX + normalizeScope(scope);
    }

    public void heartbeat() {
        try (Jedis jedis = pool.getResource()) {
            String holder = jedis.get(key);
            if (serverId.equals(holder)) {
                authority.set("OK".equals(jedis.set(key, serverId, SetParams.setParams().xx().ex(ttlSeconds))));
            } else if (holder == null) {
                authority.set("OK".equals(jedis.set(key, serverId, SetParams.setParams().nx().ex(ttlSeconds))));
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

    public void release() {
        try (Jedis jedis = pool.getResource()) {
            if (serverId.equals(jedis.get(key))) {
                jedis.del(key);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TimeSync] Failed to release authority lock: " + e.getMessage());
        }
        authority.set(false);
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "survival" : scope.toLowerCase(Locale.ROOT);
    }
}
