package com.kammoun.raven.pearlstasis.store;

import com.kammoun.raven.library.pearlstasis.PearlStasisKeys;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.logging.Logger;

public final class PearlStasisStore {

    private final JedisPool pool;
    private final Logger logger;

    public PearlStasisStore(JedisPool pool, Logger logger) {
        this.pool = pool;
        this.logger = logger;
    }

    public void setMarker(UUID playerUuid, int ttlSeconds) {
        if (pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            if (ttlSeconds > 0) {
                jedis.setex(PearlStasisKeys.markerKey(playerUuid), ttlSeconds, "1");
            } else {
                jedis.set(PearlStasisKeys.markerKey(playerUuid), "1");
            }
        } catch (Exception e) {
            logger.warning("[PearlStasis] Failed to set Redis marker for " + playerUuid + ": " + e.getMessage());
        }
    }

    public boolean hasMarker(UUID playerUuid) {
        if (pool == null) return false;
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(PearlStasisKeys.markerKey(playerUuid));
        } catch (Exception e) {
            logger.warning("[PearlStasis] Failed to check Redis marker for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    public void clearMarker(UUID playerUuid) {
        if (pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(PearlStasisKeys.markerKey(playerUuid));
        } catch (Exception e) {
            logger.warning("[PearlStasis] Failed to clear Redis marker for " + playerUuid + ": " + e.getMessage());
        }
    }
}
