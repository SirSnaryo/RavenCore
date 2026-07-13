package com.kammoun.raven.rtp.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.rtp.pool.PooledLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Mirrors {@code spawn.LocationTransferHelper}: stash the destination popped from the
 * target server's pool in Redis with a short TTL, connect the player over, and on the
 * target server's PlayerJoinEvent read-and-delete the key and teleport.
 */
public final class RtpTransferHelper {

    private static final String KEY_PREFIX = "raven:rtp:destination:";
    private static final Gson GSON = new Gson();

    private RtpTransferHelper() {}

    public static void transfer(JavaPlugin plugin, JedisPool pool, BungeeHelper bungeeHelper,
                                 Player player, PooledLocation destination, String targetServer) {
        String key = KEY_PREFIX + player.getUniqueId();
        String json = GSON.toJson(destination);
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, 30, json);
            } catch (Exception e) {
                plugin.getLogger().warning("[Rtp] Failed to store destination for "
                        + player.getName() + ": " + e.getMessage());
            }
            bungeeHelper.connectToServer(player, targetServer);
        });
    }

    public static void consumeOnJoin(JavaPlugin plugin, JedisPool pool, Player player) {
        String key = KEY_PREFIX + player.getUniqueId();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            String json;
            try (Jedis jedis = pool.getResource()) {
                json = jedis.getDel(key);
            } catch (Exception e) {
                plugin.getLogger().warning("[Rtp] Failed to read destination for "
                        + player.getName() + ": " + e.getMessage());
                return;
            }
            if (json == null) return;

            PooledLocation destination;
            try {
                destination = GSON.fromJson(json, PooledLocation.class);
            } catch (JsonSyntaxException e) {
                plugin.getLogger().warning("[Rtp] Malformed destination for " + player.getName());
                return;
            }

            World world = Bukkit.getWorld(destination.world);
            if (world == null) {
                plugin.getLogger().warning("[Rtp] World not found for destination: " + destination.world);
                return;
            }

            Location dest = new Location(world, destination.x, destination.y, destination.z, destination.yaw, 0f);
            player.getScheduler().run(plugin, t -> player.teleportAsync(dest), null);
        });
    }
}
