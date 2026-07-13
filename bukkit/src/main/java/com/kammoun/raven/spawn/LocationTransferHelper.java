package com.kammoun.raven.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.library.location.NetworkLocation;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Mirrors the shape of {@code PortalTransferHelper}: stash the destination pose in
 * Redis with a short TTL, connect the player via the legacy BungeeCord plugin-message
 * channel (Velocity intercepts it natively, no proxy-side code needed), then on the
 * target server's PlayerJoinEvent, read-and-delete the key and teleport.
 *
 * Unlike the portal helper, the payload carries a full pose (world + yaw/pitch, not
 * just x:y:z) since /spawn and /afk destinations aren't always world index 0.
 */
public final class LocationTransferHelper {

    private static final String KEY_PREFIX = "raven:spawn:destination:";
    private static final Gson GSON = new Gson();

    private LocationTransferHelper() {}

    public static void transfer(JavaPlugin plugin, JedisPool pool, BungeeHelper bungeeHelper,
                                 Player player, NetworkLocation destination) {
        String key = KEY_PREFIX + player.getUniqueId();
        String json = GSON.toJson(new TransferPayload(destination));
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, 30, json);
            } catch (Exception e) {
                plugin.getLogger().warning("[Spawn] Failed to store destination for "
                        + player.getName() + ": " + e.getMessage());
            }
            bungeeHelper.connectToServer(player, destination.getServerName());
        });
    }

    public static void consumeOnJoin(JavaPlugin plugin, JedisPool pool, Player player) {
        String key = KEY_PREFIX + player.getUniqueId();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            String json;
            try (Jedis jedis = pool.getResource()) {
                json = jedis.getDel(key);
            } catch (Exception e) {
                plugin.getLogger().warning("[Spawn] Failed to read destination for "
                        + player.getName() + ": " + e.getMessage());
                return;
            }
            if (json == null) return;

            TransferPayload payload;
            try {
                payload = GSON.fromJson(json, TransferPayload.class);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[Spawn] Malformed destination for " + player.getName());
                return;
            }

            World world = Bukkit.getWorld(payload.world);
            if (world == null) {
                plugin.getLogger().warning("[Spawn] World not found for destination: " + payload.world);
                return;
            }

            Location dest = new Location(world, payload.x, payload.y, payload.z, payload.yaw, payload.pitch);
            player.getScheduler().run(plugin, t -> player.teleportAsync(dest), null);
        });
    }

    private static final class TransferPayload {
        final String world;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        TransferPayload(NetworkLocation loc) {
            this.world = loc.getWorld();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
        }
    }
}
