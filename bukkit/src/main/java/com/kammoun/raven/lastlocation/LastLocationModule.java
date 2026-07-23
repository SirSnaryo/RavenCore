package com.kammoun.raven.lastlocation;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class LastLocationModule extends KModule<RavenCore> {

    private static final String KEY_PREFIX = "raven:lastlocation:";

    private JedisPool jedisPool;
    private String serverName;

    public LastLocationModule() {
        super("lastlocation");
    }

    @Override
    public void onModuleEnable() {
        serverName = RavenCore.getInstance().getServerId();
        jedisPool = RavenCore.getInstance().getRedisMessenger().getConnection().getPool();

        onEvent(PlayerQuitEvent.class, this::onQuit);
        onEvent(PlayerJoinEvent.class, this::onJoin);

        getLogger().info("[LastLocation] Enabled on server: " + serverName);
    }

    @Override
    public void onModuleDisable() {
        // Pool is owned by RavenCore — do not close it here.
        getLogger().info("[LastLocation] Disabled.");
    }

    private void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();

        String key = KEY_PREFIX + player.getUniqueId() + ":" + serverName;
        String value = loc.getWorld().getName()
                + ":" + loc.getX()
                + ":" + loc.getY()
                + ":" + loc.getZ()
                + ":" + loc.getYaw()
                + ":" + loc.getPitch();

        Bukkit.getAsyncScheduler().runNow(api, task -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set(key, value);
            } catch (Exception e) {
                getLogger().warning("[LastLocation] Failed to save location for "
                        + player.getName() + ": " + e.getMessage());
            }
        });
    }

    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String key = KEY_PREFIX + player.getUniqueId() + ":" + serverName;

        Bukkit.getAsyncScheduler().runNow(api, task -> {
            // A portal/border transfer already has a specific destination in flight for this
            // join — let it win instead of racing it back to wherever we were last on this server.
            if (hasPendingTransfer(player)) return;

            String value;
            try (Jedis jedis = jedisPool.getResource()) {
                value = jedis.get(key);
            } catch (Exception e) {
                getLogger().warning("[LastLocation] Failed to read location for "
                        + player.getName() + ": " + e.getMessage());
                return;
            }

            if (value == null) return;

            String[] parts = value.split(":");
            if (parts.length != 6) {
                getLogger().warning("[LastLocation] Malformed location data for "
                        + player.getName() + ": " + value);
                return;
            }

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                getLogger().warning("[LastLocation] World not found: " + parts[0]);
                return;
            }

            double x, y, z;
            float yaw, pitch;
            try {
                x = Double.parseDouble(parts[1]);
                y = Double.parseDouble(parts[2]);
                z = Double.parseDouble(parts[3]);
                yaw = Float.parseFloat(parts[4]);
                pitch = Float.parseFloat(parts[5]);
            } catch (NumberFormatException e) {
                getLogger().warning("[LastLocation] Failed to parse coordinates for "
                        + player.getName() + ": " + e.getMessage());
                return;
            }

            Location location = new Location(world, x, y, z, yaw, pitch);
            player.getScheduler().run(api, t -> player.teleportAsync(location), null);
        });
    }

    private boolean hasPendingTransfer(Player player) {
        String uuid = player.getUniqueId().toString();
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(
                    "raven:border:arrival:" + uuid,
                    "raven:spawn:destination:" + uuid,
                    "raven:teleport:destination:" + uuid,
                    "raven:teleport:pending:" + uuid,
                    "raven:respawn:pending:" + uuid) > 0;
        } catch (Exception e) {
            getLogger().warning("[LastLocation] Failed to check pending transfer for "
                    + player.getName() + ": " + e.getMessage());
            return true;
        }
    }
}
