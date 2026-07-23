package com.kammoun.raven.rtpzone.manager;

import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.platform.title.KTitle;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.rtpzone.RtpZoneModule;
import com.kammoun.raven.tpa.transfer.TeleportTransferStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Sends the whole zone group to one shared safe overworld location. */
public final class ZoneTeleportManager {
    private final RtpZoneModule module;
    private final LocationPool locationPool;
    private final List<String> clusters;
    private final TeleportTransferStore transferStore;

    public ZoneTeleportManager(RtpZoneModule module, LocationPool locationPool, Collection<String> clusters) {
        this.module = module;
        this.locationPool = locationPool;
        this.clusters = List.copyOf(clusters);
        this.transferStore = new TeleportTransferStore(module.getApi(),
                module.getApi().getRedisMessenger().getConnection().getPool());
    }

    public void teleportAll(Set<Player> players) {
        if (players.isEmpty()) return;
        if (clusters.isEmpty()) {
            module.getLogger().warning("[RtpZone] No overworld clusters configured in rtp/config.yml.");
            return;
        }
        SchedulerUtil.runAsync(() -> {
            Optional<RtpLocation> location = locationPool.getRandomLocationAcrossClusters(
                    clusters, LocationDimension.OVERWORLD);
            SchedulerUtil.run(() -> resolve(players, location));
        });
    }

    private void resolve(Set<Player> players, Optional<RtpLocation> selected) {
        if (selected.isEmpty()) {
            players.stream().filter(Player::isOnline)
                    .forEach(player -> player.sendMessage(module.getZoneConfig().getPoolEmptyMessage()));
            return;
        }
        RtpLocation location = selected.get();
        boolean local = location.serverId().equalsIgnoreCase(module.getApi().getServerId());
        for (Player player : players) {
            if (!player.isOnline()) continue;
            sendOff(player);
            if (local) teleportLocally(player, location);
            else teleportRemotely(player, location);
        }
    }

    private void teleportLocally(Player player, RtpLocation target) {
        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            player.sendMessage(module.getZoneConfig().getPoolEmptyMessage());
            return;
        }
        player.teleportAsync(new Location(world, target.x(), target.y(), target.z(),
                target.yaw(), target.pitch()));
    }

    private void teleportRemotely(Player player, RtpLocation target) {
        boolean stored = transferStore.store(new TeleportTransferStore.CoordinateTarget(
                player.getUniqueId(), target.world(), target.x(), target.y(), target.z(),
                target.yaw(), target.pitch()));
        if (!stored) {
            player.sendMessage(module.getZoneConfig().getPoolEmptyMessage());
            return;
        }
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(target.serverId());
        event.setWorld(target.world());
        event.setX(target.x());
        event.setY(target.y());
        event.setZ(target.z());
        event.setYaw(target.yaw());
        event.setPitch(target.pitch());
        module.getApi().getNetworkEventBus().publish(event);
    }

    private void sendOff(Player player) {
        SchedulerUtil.execute(player, () -> {
            KTitle title = module.getZoneConfig().getTeleportedTitle();
            if (title != null) title.send(player);
            module.getSoundManager().playTeleported(player);
        });
    }
}
