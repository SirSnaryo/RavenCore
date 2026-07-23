package com.kammoun.raven.borders.registery;

import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPoolKeys;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.logging.Logger;

/**
 * Looks up which server owns a given (cluster, dimension, cellX, cellZ) tuple.
 *
 * Redis hash key: {@code raven:grid:<cluster>:<dimension>}
 * Field: {@code <cellX>:<cellZ>}  →  server-id
 *
 * Ops populate this hash from region-registry.yml (or manually via redis-cli).
 */
public class BorderRegistry {

    private static final String KEY_PREFIX = "raven:grid:";

    private final JedisPool pool;
    private final Logger logger;

    public BorderRegistry(JedisPool pool, Logger logger) {
        this.pool = pool;
        this.logger = logger;
    }

    /**
     * Returns the server ID that owns the given cell, or null if unknown/offline.
     *
     * @param cluster   e.g. "na" or "eu"
     * @param dimension "NORMAL" or "NETHER"
     * @param cellX     neighbor cell column
     * @param cellZ     neighbor cell row
     */
    public String resolve(String cluster, String dimension, int cellX, int cellZ) {
        if (pool == null) return null;
        String key = KEY_PREFIX + cluster + ":" + dimension;
        String field = cellX + ":" + cellZ;
        try (Jedis jedis = pool.getResource()) {
            String target = jedis.hget(key, field);
            if (target != null) {
                return target;
            }
            LocationDimension locationDimension;
            try {
                locationDimension = switch (dimension) {
                    case "NORMAL" -> LocationDimension.OVERWORLD;
                    case "NETHER" -> LocationDimension.NETHER;
                    case "THE_END" -> LocationDimension.END;
                    default -> null;
                };
            } catch (IllegalArgumentException ignored) {
                locationDimension = null;
            }
            return locationDimension == null ? null
                    : jedis.hget(LocationPoolKeys.globalGridRegistry(locationDimension), field);
        } catch (Exception e) {
            logger.warning("[Borders] Registry lookup failed for " + key + " " + field + ": " + e.getMessage());
            return null;
        }
    }
}
