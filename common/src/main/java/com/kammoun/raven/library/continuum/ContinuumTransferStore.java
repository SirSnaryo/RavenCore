package com.kammoun.raven.library.continuum;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.logging.Logger;

public final class ContinuumTransferStore {

    private static final String KEY_PREFIX = "raven:continuum:transfer:";
    private static final int DEFAULT_TTL_SECONDS = 30;
    private static final Gson GSON = new Gson();

    private final JedisPool pool;
    private final Logger logger;
    private final int ttlSeconds;

    public ContinuumTransferStore(JedisPool pool, Logger logger) {
        this(pool, logger, DEFAULT_TTL_SECONDS);
    }

    public ContinuumTransferStore(JedisPool pool, Logger logger, int ttlSeconds) {
        this.pool = pool;
        this.logger = logger;
        this.ttlSeconds = Math.max(5, ttlSeconds);
    }

    public boolean store(UUID playerUuid, int clientEntityId, String targetServer, String dimensionKey) {
        if (pool == null || playerUuid == null || clientEntityId <= 0
                || targetServer == null || targetServer.isBlank()) {
            return false;
        }
        ContinuumTransferMetadata metadata = new ContinuumTransferMetadata(
                clientEntityId,
                targetServer,
                dimensionKey,
                System.currentTimeMillis()
        );
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + playerUuid, ttlSeconds, GSON.toJson(metadata));
            return true;
        } catch (Exception exception) {
            if (logger != null) {
                logger.warning("[Continuum] Failed to store transfer metadata for "
                        + playerUuid + ": " + exception.getMessage());
            }
            return false;
        }
    }

    public ContinuumTransferMetadata consume(UUID playerUuid, String currentServer) {
        if (pool == null || playerUuid == null) {
            return null;
        }
        String json;
        try (Jedis jedis = pool.getResource()) {
            json = jedis.getDel(KEY_PREFIX + playerUuid);
        } catch (Exception exception) {
            if (logger != null) {
                logger.warning("[Continuum] Failed to read transfer metadata for "
                        + playerUuid + ": " + exception.getMessage());
            }
            return null;
        }
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            ContinuumTransferMetadata metadata = GSON.fromJson(json, ContinuumTransferMetadata.class);
            long maxAgeMillis = ttlSeconds * 1000L;
            return metadata != null && metadata.usableFor(currentServer, maxAgeMillis) ? metadata : null;
        } catch (JsonSyntaxException exception) {
            if (logger != null) {
                logger.warning("[Continuum] Malformed transfer metadata for " + playerUuid);
            }
            return null;
        }
    }
}
