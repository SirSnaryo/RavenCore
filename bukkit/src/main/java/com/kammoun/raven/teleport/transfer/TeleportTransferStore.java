package com.kammoun.raven.teleport.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.teleport.TeleportModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

public class TeleportTransferStore {

    private static final String KEY_PREFIX = "raven:teleport:destination:";
    private static final int TTL_SECONDS = 45;
    private static final Gson GSON = new Gson();

    private final RavenCore plugin;
    private final JedisPool pool;

    public TeleportTransferStore(RavenCore plugin, JedisPool pool) {
        this.plugin = plugin;
        this.pool = pool;
    }

    public boolean store(PlayerTarget payload) {
        return store(payload.playerUuid(), payload);
    }

    public boolean store(CoordinateTarget payload) {
        return store(payload.playerUuid(), payload);
    }

    public PendingTeleport consume(UUID playerUuid) {
        if (pool == null) {
            return null;
        }
        String json;
        try (Jedis jedis = pool.getResource()) {
            json = jedis.getDel(KEY_PREFIX + playerUuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[Teleport] Failed to read transfer payload for " + playerUuid + ": " + e.getMessage());
            return null;
        }

        if (json == null) {
            return null;
        }

        try {
            PendingTeleport pending = GSON.fromJson(json, PendingTeleport.class);
            return pending != null && pending.type != null ? pending : null;
        } catch (JsonSyntaxException e) {
            plugin.getLogger().warning("[Teleport] Malformed transfer payload for " + playerUuid);
            return null;
        }
    }

    public boolean apply(Player player, TeleportModule module) {
        PendingTeleport pending = consume(player.getUniqueId());
        if (pending == null) {
            return false;
        }

        player.getScheduler().runDelayed(plugin, task -> {
            if (pending.destinationUuid != null) {
                try {
                    Player destination = Bukkit.getPlayer(UUID.fromString(pending.destinationUuid));
                    if (destination != null) {
                        player.teleportAsync(destination.getLocation());
                    }
                } catch (RuntimeException ignored) {
                }
                return;
            }

            module.teleportLocal(player, pending.world, pending.x, pending.y, pending.z, pending.yaw, pending.pitch);
        }, null, 20L);
        return true;
    }

    private boolean store(UUID playerUuid, Object payload) {
        if (pool == null) {
            return false;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + playerUuid, TTL_SECONDS, GSON.toJson(payload));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Teleport] Failed to store transfer payload for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    public record PlayerTarget(UUID playerUuid, String destinationUuid, String type) {
        public PlayerTarget(UUID playerUuid, UUID destinationUuid) {
            this(playerUuid, destinationUuid.toString(), "PLAYER");
        }
    }

    public record CoordinateTarget(UUID playerUuid, String world, double x, double y, double z,
                                   float yaw, float pitch, String type) {
        public CoordinateTarget(UUID playerUuid, String world, double x, double y, double z, float yaw, float pitch) {
            this(playerUuid, world, x, y, z, yaw, pitch, "COORDINATE");
        }
    }

    public static final class PendingTeleport {
        private String type;
        private String destinationUuid;
        private String world;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
    }
}
