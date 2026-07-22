package com.kammoun.raven.borders.utils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Stores a pending border-arrival record in Redis so the target server can
 * place the player at the exact crossing coordinate instead of world spawn.
 */
public class BorderTransferStore {

    private static final String KEY_PREFIX = "raven:border:arrival:";
    private static final int TTL_SECONDS = 30;
    private static final Gson GSON = new Gson();

    private final JedisPool pool;
    private final Logger logger;

    public BorderTransferStore(JedisPool pool, Logger logger) {
        this.pool = pool;
        this.logger = logger;
    }

    public void store(UUID playerUuid, String dimension, double x, double y, double z, float yaw, float pitch) {
        if (pool == null) return;
        Arrival arrival = new Arrival(dimension, x, y, z, yaw, pitch);
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + playerUuid, TTL_SECONDS, GSON.toJson(arrival));
        } catch (Exception e) {
            logger.warning("[Borders] Failed to store arrival for " + playerUuid + ": " + e.getMessage());
        }
    }

    /** Returns and deletes the pending arrival, or null if none. */
    public Arrival consume(UUID playerUuid) {
        if (pool == null) return null;
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.getDel(KEY_PREFIX + playerUuid);
            if (json == null) return null;
            return GSON.fromJson(json, Arrival.class);
        } catch (JsonSyntaxException e) {
            logger.warning("[Borders] Malformed arrival payload for " + playerUuid);
            return null;
        } catch (Exception e) {
            logger.warning("[Borders] Failed to read arrival for " + playerUuid + ": " + e.getMessage());
            return null;
        }
    }

    public record Arrival(String dimension, double x, double y, double z, float yaw, float pitch) {}
}
