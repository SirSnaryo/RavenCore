package com.kammoun.raven.respawn.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.respawn.bed.BedSpawnRecord;
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

    public boolean storeCoordinate(UUID playerUuid, String serverId, String world,
                                   double x, double y, double z, float yaw, float pitch) {
        return store(playerUuid, new Payload("COORDINATE", serverId, world, x, y, z, yaw, pitch));
    }

    public ConsumeResult consume(UUID playerUuid) {
        return read(playerUuid, true);
    }

    public ConsumeResult peek(UUID playerUuid) {
        return read(playerUuid, false);
    }

    public void delete(UUID playerUuid) {
        if (pool == null) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(PAYLOAD_PREFIX + playerUuid);
            jedis.del(MARKER_PREFIX + playerUuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[Respawn] Failed to delete transfer payload: " + e.getMessage());
        }
    }

    private ConsumeResult read(UUID playerUuid, boolean consume) {
        if (pool == null) return ConsumeResult.unavailable();
        try (Jedis jedis = pool.getResource()) {
            boolean pending = jedis.exists(MARKER_PREFIX + playerUuid);
            String json = consume ? jedis.getDel(PAYLOAD_PREFIX + playerUuid) : jedis.get(PAYLOAD_PREFIX + playerUuid);
            if (consume) jedis.del(MARKER_PREFIX + playerUuid);
            if (json == null) {
                return pending ? ConsumeResult.invalid() : ConsumeResult.none();
            }
            Payload payload = GSON.fromJson(json, Payload.class);
            if (payload == null || (!"BED".equals(payload.type()) && !"COORDINATE".equals(payload.type())
                    && !"RANDOM".equals(payload.type()))
                    || payload.serverId() == null
                    || payload.world() == null || payload.serverId().isBlank() || payload.world().isBlank()
                    || !Double.isFinite(payload.x()) || !Double.isFinite(payload.y())
                    || !Double.isFinite(payload.z()) || !Float.isFinite(payload.yaw())
                    || !Float.isFinite(payload.pitch())) {
                return ConsumeResult.invalid();
            }
            return ConsumeResult.payload(payload);
        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[Respawn] Malformed transfer payload for " + playerUuid);
            return ConsumeResult.invalid();
        } catch (Exception e) {
            plugin.getLogger().warning("[Respawn] Failed to consume transfer payload: " + e.getMessage());
            return ConsumeResult.unavailable();
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

    public enum ConsumeStatus {
        NONE,
        PAYLOAD,
        INVALID,
        UNAVAILABLE
    }

    public record ConsumeResult(ConsumeStatus status, Payload payload) {
        public static ConsumeResult none() {
            return new ConsumeResult(ConsumeStatus.NONE, null);
        }

        public static ConsumeResult payload(Payload payload) {
            return new ConsumeResult(ConsumeStatus.PAYLOAD, payload);
        }

        public static ConsumeResult invalid() {
            return new ConsumeResult(ConsumeStatus.INVALID, null);
        }

        public static ConsumeResult unavailable() {
            return new ConsumeResult(ConsumeStatus.UNAVAILABLE, null);
        }
    }
}
