package com.kammoun.raven.rtp.pool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

/**
 * Redis-backed queue of {@link PooledLocation}s per server id (key {@code raven:rtp:pool:<serverId>}).
 * A plain Redis LIST: RPUSH to add, LPOP to take — LPOP is atomic on the Redis server, so any
 * server on the network can safely take the next location for a target server's pool without
 * that target server's JVM being involved. All methods do blocking Jedis I/O; callers are
 * responsible for running them off the main thread.
 */
public class RtpPoolStore {

    private static final String KEY_PREFIX = "raven:rtp:pool:";
    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final JedisPool pool;

    public RtpPoolStore(JavaPlugin plugin, JedisPool pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    public void push(String serverId, PooledLocation location) {
        String json = GSON.toJson(location);
        try (Jedis jedis = pool.getResource()) {
            jedis.rpush(KEY_PREFIX + serverId, json);
        } catch (Exception e) {
            plugin.getLogger().warning("[Rtp] Failed to push pool location for " + serverId + ": " + e.getMessage());
        }
    }

    public long size(String serverId) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.llen(KEY_PREFIX + serverId);
        } catch (Exception e) {
            plugin.getLogger().warning("[Rtp] Failed to read pool size for " + serverId + ": " + e.getMessage());
            return 0;
        }
    }

    public Optional<PooledLocation> pop(String serverId) {
        String json;
        try (Jedis jedis = pool.getResource()) {
            json = jedis.lpop(KEY_PREFIX + serverId);
        } catch (Exception e) {
            plugin.getLogger().warning("[Rtp] Failed to pop pool location for " + serverId + ": " + e.getMessage());
            return Optional.empty();
        }
        if (json == null) return Optional.empty();

        try {
            return Optional.of(GSON.fromJson(json, PooledLocation.class));
        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[Rtp] Malformed pool entry for " + serverId);
            return Optional.empty();
        }
    }
}
