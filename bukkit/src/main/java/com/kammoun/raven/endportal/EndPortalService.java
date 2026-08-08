package com.kammoun.raven.endportal;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.endportal.config.EndPortalConfig;
import com.kammoun.raven.endportal.config.EndPortalMessage;
import com.kammoun.raven.endportal.config.EndPortalMessages;
import com.kammoun.raven.endportal.config.EndPortalSounds;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.tpa.transfer.TeleportTransferStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Resolves an End-portal crossing into a destination and hands the player to the
 * proxy. Both directions share the same path: pop a pre-validated location out of
 * the Location Library, park it in Redis for the target server, then publish the
 * transfer so Velocity moves the connection.
 */
public final class EndPortalService {

    private static final long BORDER_SUPPRESSION_MILLIS = 30_000L;

    private final RavenCore plugin;
    private final EndPortalConfig config;
    private final EndPortalMessages messages;
    private final EndPortalSounds sounds;
    private final TeleportTransferStore transfers;
    private final Map<UUID, Long> recentlyTriggered = new ConcurrentHashMap<>();

    public EndPortalService(RavenCore plugin, EndPortalConfig config,
                            EndPortalMessages messages, EndPortalSounds sounds) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
        this.transfers = new TeleportTransferStore(plugin,
                plugin.getRedisMessenger().getConnection().getPool());
    }

    public void sendToEnd(Player player) {
        if (config.endServer().equalsIgnoreCase(plugin.getServerId())) {
            plugin.getLogger().warning("[EndPortal] end-server points at this server ("
                    + plugin.getServerId() + "); ignoring the crossing.");
            return;
        }
        resolveAndSend(player, () -> {
            LocationPool pool = pool();
            return pool == null ? Optional.<RtpLocation>empty()
                    : pool.getRandomLocation(config.endServer(), LocationDimension.END);
        }, EndPortalMessage.ENTERING, EndPortalMessage.NO_END_LOCATION);
    }

    public void sendToOverworld(Player player) {
        resolveAndSend(player, this::randomOverworldLocation,
                EndPortalMessage.RETURNING, EndPortalMessage.NO_RETURN_LOCATION);
    }

    /**
     * Claims the player for a crossing. Portal blocks are entered for several ticks in
     * a row and the portal, respawn and block paths can all fire for one crossing, so
     * every entry point funnels through here to keep exactly one transfer in flight.
     */
    public boolean claim(Player player) {
        long now = System.currentTimeMillis();
        boolean[] claimed = {false};
        recentlyTriggered.compute(player.getUniqueId(), (uuid, expiry) -> {
            if (expiry != null && expiry > now) {
                return expiry;
            }
            claimed[0] = true;
            return now + config.retriggerCooldownMillis();
        });
        if (!claimed[0]) {
            return false;
        }
        // Denied crossings keep the claim so a player held in combat inside the portal
        // gets the combat warning once per cooldown instead of once per movement tick.
        return plugin.getCombatModule() == null || plugin.getCombatModule().canTransfer(player, true);
    }

    public void release(UUID playerUuid) {
        recentlyTriggered.remove(playerUuid);
    }

    public void stop() {
        recentlyTriggered.clear();
    }

    private void resolveAndSend(Player player, Supplier<Optional<RtpLocation>> destination,
                                EndPortalMessage started, EndPortalMessage unavailable) {
        messages.send(player, started);
        sounds.play(player, "information-click");

        SchedulerUtil.runAsync(() -> {
            Optional<RtpLocation> selected = destination.get();
            SchedulerUtil.execute(player, () -> {
                if (!player.isOnline()) {
                    release(player.getUniqueId());
                    return;
                }
                if (selected.isEmpty()) {
                    fail(player, unavailable);
                    return;
                }
                transfer(player, selected.get());
            });
        });
    }

    /**
     * Picks one cluster uniformly rather than one server, so a cluster running more
     * servers than its neighbour doesn't attract proportionally more returning players.
     * A drained cluster falls back to the others instead of stranding the player.
     */
    private Optional<RtpLocation> randomOverworldLocation() {
        LocationPool pool = pool();
        if (pool == null) return Optional.empty();

        List<String> clusters = config.returnClusters();
        if (clusters.isEmpty()) {
            return pool.getRandomLocationGlobal(LocationDimension.OVERWORLD);
        }

        String picked = clusters.get(ThreadLocalRandom.current().nextInt(clusters.size()));
        Optional<RtpLocation> chosen = pool.getRandomLocationInCluster(picked, LocationDimension.OVERWORLD);
        if (chosen.isPresent()) {
            return chosen;
        }

        List<String> remaining = new ArrayList<>(clusters);
        remaining.remove(picked);
        if (remaining.isEmpty()) {
            return pool.getRandomLocationGlobal(LocationDimension.OVERWORLD);
        }
        return pool.getRandomLocationAcrossClusters(remaining, LocationDimension.OVERWORLD);
    }

    private void transfer(Player player, RtpLocation target) {
        if (target.serverId().equalsIgnoreCase(plugin.getServerId())) {
            teleportLocally(player, target);
            return;
        }

        boolean stored = transfers.store(new TeleportTransferStore.CoordinateTarget(
                player.getUniqueId(), target.world(), target.x(), target.y(), target.z(),
                target.yaw(), target.pitch()));
        if (!stored) {
            fail(player, EndPortalMessage.TRANSFER_FAILED);
            return;
        }

        suppressBorders(player);
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(target.serverId());
        event.setWorld(target.world());
        event.setX(target.x());
        event.setY(target.y());
        event.setZ(target.z());
        event.setYaw(target.yaw());
        event.setPitch(target.pitch());
        plugin.getNetworkEventBus().publish(event);
    }

    /** The pool handed back a location this server owns — no proxy hop needed. */
    private void teleportLocally(Player player, RtpLocation target) {
        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            fail(player, EndPortalMessage.TRANSFER_FAILED);
            return;
        }
        player.teleportAsync(new Location(world, target.x(), target.y(), target.z(),
                target.yaw(), target.pitch()));
    }

    /** Keeps the claim so a player stood in the portal retries on the cooldown, not every tick. */
    private void fail(Player player, EndPortalMessage message) {
        messages.send(player, message);
        sounds.play(player, "error");
    }

    private void suppressBorders(Player player) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().suppressTransfers(player.getUniqueId(), BORDER_SUPPRESSION_MILLIS);
        }
    }

    private LocationPool pool() {
        return plugin.getLocationPoolModule() == null ? null : plugin.getLocationPoolModule().getPool();
    }
}
