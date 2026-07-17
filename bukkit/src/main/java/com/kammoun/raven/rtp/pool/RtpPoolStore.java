package com.kammoun.raven.rtp.pool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

/**
 * Redis-backed queue of {@link PooledLocation}s per RTP destination id (key
 * {@code raven:rtp:pool:<destinationId>}). Keyed by destination rather than server id since one
 * survival server now backs two destinations at once (its Overworld region and that region's
 * local Nether). A plain Redis LIST: RPUSH to add, LPOP to take — LPOP is atomic on the Redis
 * server, so any server on the network can safely take the next location for a destination's
 * pool without that destination's JVM being involved. All methods do blocking Jedis I/O; callers
 * are responsible for running them off the main thread.
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

    public void push(String destinationId, PooledLocation location) {
        String json = GSON.toJson(location);
        try (Jedis jedis = pool.getResource()) {
            jedis.rpush(KEY_PREFIX + destinationId, json);
        } catch (Exception e) {
            plugin.getLogger().warning("[Rtp] Failed to push pool location for " + destinationId + ": " + e.getMessage());
        }
    }

    public long size(String destinationId) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.llen(KEY_PREFIX + destinationId);
        } catch (Exception e) {
            plugin.getLogger().warning("[Rtp] Failed to read pool size for " + destinationId + ": " + e.getMessage());
            return 0;
        }
    }

    public Optional<PooledLocation> pop(String destinationId) {
        String json;
        try (Jedis jedis = pool.getResource()) {
            json = jedis.lpop(KEY_PREFIX + destinationId);
        } catch (Exception e) {
            plugin.getLogger().warning("[Rtp] Failed to pop pool location for " + destinationId + ": " + e.getMessage());
            return Optional.empty();
        }
        if (json == null) return Optional.empty();

        try {
            return Optional.of(GSON.fromJson(json, PooledLocation.class));
        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[Rtp] Malformed pool entry for " + destinationId);
            return Optional.empty();
        }
    }
}
