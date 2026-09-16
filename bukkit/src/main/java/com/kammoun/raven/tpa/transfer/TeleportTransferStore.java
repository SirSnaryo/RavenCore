package com.kammoun.raven.tpa.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.tpa.TeleportModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TeleportTransferStore {

    private static final String KEY_PREFIX = "raven:teleport:destination:";
    private static final String MARKER_PREFIX = "raven:teleport:pending:";
    private static final int TTL_SECONDS = 45;
    private static final int OFFLINE_TTL_SECONDS = 86400;
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

    public boolean storeOffline(PlayerTarget payload) {
        return store(payload.playerUuid(), payload, OFFLINE_TTL_SECONDS);
    }

    public boolean store(CoordinateTarget payload) {
        return store(payload.playerUuid(), payload);
    }

    public boolean storeOffline(CoordinateTarget payload) {
        return store(payload.playerUuid(), payload, OFFLINE_TTL_SECONDS);
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
            if (pending.server != null && !pending.server.isBlank()
                    && !pending.server.equalsIgnoreCase(module.currentServer())) {
                if (pending.destinationUuid != null) {
                    store(player.getUniqueId(), new PlayerTarget(player.getUniqueId(), pending.server, pending.destinationUuid, "PLAYER"));
                } else {
                    store(player.getUniqueId(), new CoordinateTarget(player.getUniqueId(), pending.server, pending.world,
                            pending.x, pending.y, pending.z, pending.yaw, pending.pitch));
                }
                CrossServerTeleportEvent event = new CrossServerTeleportEvent();
                event.setPlayerUuid(player.getUniqueId().toString());
                event.setTargetServer(pending.server);
                event.setDestinationUuid(pending.destinationUuid);
                event.setWorld(pending.world);
                event.setX(pending.x);
                event.setY(pending.y);
                event.setZ(pending.z);
                event.setYaw(pending.yaw);
                event.setPitch(pending.pitch);
                plugin.getNetworkEventBus().publish(event);
                clearMarkerLater(player.getUniqueId());
                return;
            }

            if (pending.destinationUuid != null) {
                try {
                    Player destination = Bukkit.getPlayer(UUID.fromString(pending.destinationUuid));
                    if (destination != null) {
                        player.teleportAsync(destination.getLocation());
                    }
                } catch (RuntimeException ignored) {
                }
                clearMarkerLater(player.getUniqueId());
                return;
            }

            module.teleportLocal(player, pending.world, pending.x, pending.y, pending.z, pending.yaw, pending.pitch);
            clearMarkerLater(player.getUniqueId());
        }, null, 1L);
        return true;
    }

    private boolean store(UUID playerUuid, Object payload) {
        return store(playerUuid, payload, TTL_SECONDS);
    }

    private boolean store(UUID playerUuid, Object payload, int ttlSeconds) {
        if (pool == null) {
            return false;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(KEY_PREFIX + playerUuid, ttlSeconds, GSON.toJson(payload));
            jedis.setex(MARKER_PREFIX + playerUuid, ttlSeconds, "1");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Teleport] Failed to store transfer payload for " + playerUuid + ": " + e.getMessage());
            return false;
        }
    }

    private void clearMarkerLater(UUID playerUuid) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(MARKER_PREFIX + playerUuid);
            } catch (Exception e) {
                plugin.getLogger().warning("[Teleport] Failed to clear pending marker for "
                        + playerUuid + ": " + e.getMessage());
            }
        }, 2L, TimeUnit.SECONDS);
    }

    public record PlayerTarget(UUID playerUuid, String server, String destinationUuid, String type) {
        public PlayerTarget(UUID playerUuid, UUID destinationUuid) {
            this(playerUuid, null, destinationUuid.toString(), "PLAYER");
        }

        public PlayerTarget(UUID playerUuid, String server, UUID destinationUuid) {
            this(playerUuid, server, destinationUuid.toString(), "PLAYER");
        }
    }

    public record CoordinateTarget(UUID playerUuid, String server, String world, double x, double y, double z,
                                   float yaw, float pitch, String type) {
        public CoordinateTarget(UUID playerUuid, String world, double x, double y, double z, float yaw, float pitch) {
            this(playerUuid, null, world, x, y, z, yaw, pitch, "COORDINATE");
        }

        public CoordinateTarget(UUID playerUuid, String server, String world, double x, double y, double z, float yaw, float pitch) {
            this(playerUuid, server, world, x, y, z, yaw, pitch, "COORDINATE");
        }
    }

    public static final class PendingTeleport {
        private String type;
        private String destinationUuid;
        private String server;
        private String world;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
    }
}
