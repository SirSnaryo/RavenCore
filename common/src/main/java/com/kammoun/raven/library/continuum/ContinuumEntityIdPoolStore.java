package com.kammoun.raven.library.continuum;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.UUID;
import java.util.logging.Logger;

public final class ContinuumEntityIdPoolStore {

    private static final String PLAYER_KEY_PREFIX = "raven:continuum:entity-id:";
    private static final String COUNTER_KEY = "raven:continuum:entity-id:counter";

    private final JedisPool pool;
    private final Logger logger;

    public ContinuumEntityIdPoolStore(JedisPool pool, Logger logger) {
        this.pool = pool;
        this.logger = logger;
    }

    public int assign(UUID playerUuid, int minId, int maxId, int ttlSeconds) {
        if (pool == null || playerUuid == null || minId <= 0 || maxId < minId) {
            return 0;
        }

        int ttl = Math.max(5, ttlSeconds);
        String key = PLAYER_KEY_PREFIX + playerUuid;
        try (Jedis jedis = pool.getResource()) {
            Integer existing = parseInRange(jedis.get(key), minId, maxId);
            if (existing != null) {
                jedis.expire(key, ttl);
                return existing;
            }

            long span = (long) maxId - minId + 1L;
            for (int attempt = 0; attempt < 8; attempt++) {
                long next = jedis.incr(COUNTER_KEY);
                int candidate = minId + (int) Math.floorMod(next - 1L, span);
                String stored = jedis.set(key, String.valueOf(candidate), SetParams.setParams().nx().ex(ttl));
                if ("OK".equalsIgnoreCase(stored)) {
                    return candidate;
                }

                existing = parseInRange(jedis.get(key), minId, maxId);
                if (existing != null) {
                    jedis.expire(key, ttl);
                    return existing;
                }
            }
        } catch (Exception exception) {
            if (logger != null) {
                logger.warning("[Continuum] Failed to assign network entity id for "
                        + playerUuid + ": " + exception.getMessage());
            }
        }
        return 0;
    }

    public void retain(UUID playerUuid, int seconds) {
        if (pool == null || playerUuid == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.expire(PLAYER_KEY_PREFIX + playerUuid, Math.max(1, seconds));
        } catch (Exception exception) {
            if (logger != null) {
                logger.warning("[Continuum] Failed to retain network entity id for "
                        + playerUuid + ": " + exception.getMessage());
            }
        }
    }

    private Integer parseInRange(String value, int minId, int maxId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= minId && parsed <= maxId ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
