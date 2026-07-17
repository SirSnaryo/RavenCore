package com.kammoun.raven.portal.helper;

import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public final class PortalTransferHelper {

    static final String KEY_PREFIX = "raven:portal:destination:";

    private PortalTransferHelper() {}

    /**
     * Stores the destination in Redis for the target server to pick up on join, then sends the
     * player across. {@code targetEnvironment} is delivered alongside the coordinates so the
     * receiving server can land the player in the right world — a survival server has both an
     * Overworld and a local Nether loaded at once, and a dedicated End server can't unload its
     * default Overworld either way (Bukkit refuses to unload world index 0), so picking "the
     * first loaded world" on arrival isn't reliable without knowing which environment to match.
     */
    public static void transfer(JavaPlugin plugin, JedisPool pool, Player player,
                                double x, double y, double z, World.Environment targetEnvironment,
                                String targetServer) {
        String key   = KEY_PREFIX + player.getUniqueId();
        String value = x + ":" + y + ":" + z + ":" + targetEnvironment.name();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, 30, value);
            } catch (Exception e) {
                plugin.getLogger().warning("[Portal] Failed to store destination for "
                        + player.getName() + ": " + e.getMessage());
            }
            new BungeeHelper(plugin).connectToServer(player, targetServer);
        });
    }

    /**
     * True if a {@link #transfer} destination for this player is still waiting to be delivered.
     * Lets other on-join logic (e.g. last-location restore) yield to an in-flight portal/border
     * transfer instead of racing it for where the player ends up.
     */
    public static boolean hasPendingDestination(JedisPool pool, Player player) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(KEY_PREFIX + player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    /** On join, delivers any destination left for this player by a prior {@link #transfer} call on another server. */
    public static void deliverPendingDestination(JavaPlugin plugin, JedisPool pool, Player player) {
        String key = KEY_PREFIX + player.getUniqueId();

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            String value;
            try (Jedis jedis = pool.getResource()) {
                value = jedis.getDel(key);
            } catch (Exception e) {
                plugin.getLogger().warning("[Portal] Failed to read destination for " + player.getName() + ": " + e.getMessage());
                return;
            }

            if (value == null) return;

            String[] parts = value.split(":");
            if (parts.length != 4) return;

            try {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                World.Environment environment = World.Environment.valueOf(parts[3]);

                World world = resolveWorld(environment);
                if (world == null) {
                    plugin.getLogger().warning("[Portal] No loaded " + environment + " world to deliver "
                            + player.getName() + " into.");
                    return;
                }

                Location dest = new Location(world, x, y, z);
                player.getScheduler().run(plugin, t -> player.teleportAsync(dest), null);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Portal] Malformed destination for " + player.getName() + ": " + value);
            }
        });
    }

    /** The first loaded world matching {@code environment}, or null if this server has none. */
    private static World resolveWorld(World.Environment environment) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) return world;
        }
        return null;
    }
}
