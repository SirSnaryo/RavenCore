package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RespawnManager implements Listener {
    private final RavenCore plugin;
    private final BedSpawnRepository beds;
    private final LocationPool locations;
    private final RespawnTransferStore transfers;
    private final RespawnConfig config;
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();

    public RespawnManager(RavenCore plugin, BedSpawnRepository beds, LocationPool locations,
                          RespawnTransferStore transfers, RespawnConfig config) {
        this.plugin = plugin;
        this.beds = beds;
        this.locations = locations;
        this.transfers = transfers;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn()) return;
        Player player = event.getPlayer();
        if (plugin.getPearlStasisModule() != null && plugin.getPearlStasisModule().hasPendingStasis(player.getUniqueId())) {
            return;
        }
        suppressBorders(player);
        SchedulerUtil.runLater(() -> resolveSavedBed(player), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPearlStasisModule() != null && plugin.getPearlStasisModule().hasPendingStasis(player.getUniqueId())) {
            return;
        }
        SchedulerUtil.runAsync(() -> {
            RespawnTransferStore.Payload payload = transfers.consume(player.getUniqueId());
            if (payload == null) return;
            SchedulerUtil.execute(player, () -> {
                suppressBorders(player);
                applyIncoming(player, payload);
            });
        });
    }

    public void stop() {
        resolving.clear();
    }

    private void resolveSavedBed(Player player) {
        if (!player.isOnline() || !resolving.add(player.getUniqueId())) return;
        SchedulerUtil.runAsync(() -> {
            Optional<BedSpawnRecord> bed = Optional.empty();
            try {
                bed = beds.find(player.getUniqueId());
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().warning("[Respawn] Failed to load bed for " + player.getName() + ": " + e.getMessage());
            }
            Optional<BedSpawnRecord> selected = bed;
            SchedulerUtil.execute(player, () -> {
                resolving.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                if (selected.isPresent()) useBed(player, selected.get());
                else resolveRandom(player);
            });
        });
    }

    private void useBed(Player player, BedSpawnRecord bed) {
        if (!bed.serverId().equalsIgnoreCase(plugin.getServerId())) {
            if (!transfers.storeBed(bed)) {
                fail(player);
                return;
            }
            sendToServer(player, bed.serverId());
            return;
        }
        Optional<Location> safe = BedSpawnLocator.safeSpawn(bed);
        if (safe.isPresent()) {
            teleportAndRelease(player, safe.get());
            return;
        }
        deleteStaleBed(player.getUniqueId());
        resolveRandom(player);
    }

    private void resolveRandom(Player player) {
        if (!resolving.add(player.getUniqueId())) return;
        SchedulerUtil.runAsync(() -> {
            Optional<RtpLocation> location = locations.getRandomLocationAcrossClusters(
                    config.clusters(), LocationDimension.OVERWORLD);
            SchedulerUtil.execute(player, () -> {
                resolving.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                if (location.isEmpty()) {
                    player.sendMessage(ChatFormatter.color(config.noLocationMessage()));
                    releaseBorders(player);
                    return;
                }
                useRandom(player, location.get());
            });
        });
    }

    private void useRandom(Player player, RtpLocation location) {
        if (!location.serverId().equalsIgnoreCase(plugin.getServerId())) {
            if (!transfers.storeRandom(player.getUniqueId(), location)) {
                fail(player);
                return;
            }
            sendToServer(player, location.serverId());
            return;
        }
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            fail(player);
            return;
        }
        teleportAndRelease(player, new Location(world, location.x(), location.y(), location.z(),
                location.yaw(), location.pitch()));
    }

    private void applyIncoming(Player player, RespawnTransferStore.Payload payload) {
        if (!player.isOnline()) return;
        if (payload.isBed()) {
            BedSpawnRecord bed = new BedSpawnRecord(player.getUniqueId(), payload.serverId(), payload.world(),
                    (int) payload.x(), (int) payload.y(), (int) payload.z());
            Optional<Location> safe = BedSpawnLocator.safeSpawn(bed);
            if (safe.isPresent()) {
                teleportAndRelease(player, safe.get());
                return;
            }
            deleteStaleBed(player.getUniqueId());
            resolveRandom(player);
            return;
        }
        useRandom(player, new RtpLocation(payload.serverId(), LocationDimension.OVERWORLD,
                payload.world(), payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch()));
    }

    private void sendToServer(Player player, String serverId) {
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(serverId);
        plugin.getNetworkEventBus().publish(event);
    }

    private void deleteStaleBed(UUID playerUuid) {
        SchedulerUtil.runAsync(() -> {
            try {
                beds.delete(playerUuid);
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().warning("[Respawn] Failed to delete stale bed: " + e.getMessage());
            }
        });
    }

    private void fail(Player player) {
        player.sendMessage(ChatFormatter.color(config.transferFailedMessage()));
        releaseBorders(player);
    }

    private void teleportAndRelease(Player player, Location location) {
        player.teleportAsync(location).whenComplete((success, error) -> releaseBorders(player));
    }

    private void suppressBorders(Player player) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().suppressTransfers(player.getUniqueId(), 30_000L);
        }
    }

    private void releaseBorders(Player player) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().releaseTransfers(player.getUniqueId());
        }
    }
}
