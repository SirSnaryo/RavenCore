package com.kammoun.raven.rtpzone.manager;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.title.KTitle;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.rtp.config.RtpDestinations;
import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import com.kammoun.raven.rtp.pool.PooledLocation;
import com.kammoun.raven.rtp.pool.RtpPoolStore;
import com.kammoun.raven.rtp.transfer.RtpTransferHelper;
import com.kammoun.raven.rtpzone.RtpZoneModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends the whole zone group to a single random overworld destination, reusing the /rtp module's
 * shared Redis pools ({@link RtpPoolStore}) and cross-server transfer ({@link RtpTransferHelper}).
 * One random server is chosen for the batch and one safe location popped from its pool, so
 * everyone lands together on the exact same spot (per the module's configured behaviour).
 */
public class ZoneTeleportManager {

    private final RtpZoneModule module;
    private final RtpPoolStore poolStore;
    private final RtpDestinations destinations;

    public ZoneTeleportManager(RtpZoneModule module, RtpPoolStore poolStore, RtpDestinations destinations) {
        this.module = module;
        this.poolStore = poolStore;
        this.destinations = destinations;
    }

    /** Called on the main thread with a snapshot of the players to move. */
    public void teleportAll(Set<Player> players) {
        if (players.isEmpty()) return;

        List<RegionDestination> overworld = new ArrayList<>(destinations.overworldWorlds());
        if (overworld.isEmpty()) {
            module.getLogger().warning("[RtpZone] No overworld destinations configured in rtp/config.yml — cannot teleport.");
            return;
        }

        RegionDestination destination = overworld.get(ThreadLocalRandom.current().nextInt(overworld.size()));

        // Popping from Redis is blocking I/O — do it off the main thread, then finish back on it.
        RavenCore api = module.getApi();
        Bukkit.getAsyncScheduler().runNow(api, task -> {
            Optional<PooledLocation> location = poolStore.pop(destination.id());
            SchedulerUtil.run(() -> onDestinationResolved(players, destination, location));
        });
    }

    private void onDestinationResolved(Set<Player> players, RegionDestination destination,
                                       Optional<PooledLocation> location) {
        if (location.isEmpty()) {
            for (Player player : players) {
                if (player.isOnline()) player.sendMessage(module.getZoneConfig().getPoolEmptyMessage());
            }
            return;
        }

        PooledLocation loc = location.get();
        boolean local = destination.server().equals(module.getApi().getServerId());

        for (Player player : players) {
            if (!player.isOnline()) continue;
            sendOff(player);
            if (local) {
                teleportLocally(player, loc);
            } else {
                RtpTransferHelper.transfer(module.getApi(),
                        module.getApi().getRedisMessenger().getConnection().getPool(),
                        module.getBungeeHelper(), player, loc, destination.server());
            }
        }
    }

    private void teleportLocally(Player player, PooledLocation loc) {
        World world = Bukkit.getWorld(loc.world);
        if (world == null) {
            player.sendMessage(module.getZoneConfig().getPoolEmptyMessage());
            return;
        }
        player.teleportAsync(new Location(world, loc.x, loc.y, loc.z, loc.yaw, 0f));
    }

    /** The "whoosh" title + sound shown as the player is whisked away. */
    private void sendOff(Player player) {
        SchedulerUtil.execute(player, () -> {
            KTitle title = module.getZoneConfig().getTeleportedTitle();
            if (title != null) title.send(player);
            module.getSoundManager().playTeleported(player);
        });
    }
}
