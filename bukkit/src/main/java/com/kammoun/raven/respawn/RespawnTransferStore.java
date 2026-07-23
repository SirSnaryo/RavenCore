package com.kammoun.raven.respawn;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.RtpLocation;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

public final class RespawnTransferStore {
    private static final String PAYLOAD_PREFIX = "raven:respawn:destination:";
    private static final String MARKER_PREFIX = "raven:respawn:pending:";
    private static final int TTL_SECONDS = 45;
    private static final Gson GSON = new Gson();

    private final RavenCore plugin;
    private final JedisPool pool;

    public RespawnTransferStore(RavenCore plugin, JedisPool pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    public boolean storeBed(BedSpawnRecord bed) {
        return store(bed.playerUuid(), new Payload("BED", bed.serverId(), bed.world(),
                bed.x(), bed.y(), bed.z(), 0.0f, 0.0f));
    }

    public boolean storeRandom(UUID playerUuid, RtpLocation location) {
        return store(playerUuid, new Payload("RANDOM", location.serverId(), location.world(),
                location.x(), location.y(), location.z(), location.yaw(), location.pitch()));
    }

    public Payload consume(UUID playerUuid) {
        if (pool == null) return null;
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.getDel(PAYLOAD_PREFIX + playerUuid);
            jedis.del(MARKER_PREFIX + playerUuid);
            if (json == null) return null;
            return GSON.fromJson(json, Payload.class);
        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[Respawn] Malformed transfer payload for " + playerUuid);
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("[Respawn] Failed to consume transfer payload: " + e.getMessage());
            return null;
        }
    }

    public static boolean hasPending(JedisPool pool, UUID playerUuid) {
        if (pool == null) return false;
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(MARKER_PREFIX + playerUuid);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean store(UUID playerUuid, Payload payload) {
        if (pool == null) return false;
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(PAYLOAD_PREFIX + playerUuid, TTL_SECONDS, GSON.toJson(payload));
            jedis.setex(MARKER_PREFIX + playerUuid, TTL_SECONDS, "1");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Respawn] Failed to prepare transfer: " + e.getMessage());
            return false;
        }
    }

    public record Payload(String type, String serverId, String world,
                          double x, double y, double z, float yaw, float pitch) {
        public boolean isBed() {
            return "BED".equals(type);
        }
    }
}
