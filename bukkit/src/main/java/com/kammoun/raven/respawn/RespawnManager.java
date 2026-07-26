package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.respawn.BedSpawnLocator.BedSpawnResolution;
import com.kammoun.raven.respawn.BedSpawnLocator.BedSpawnStatus;
import com.kammoun.raven.respawn.RespawnTransferStore.ConsumeResult;
import com.kammoun.raven.respawn.RespawnTransferStore.ConsumeStatus;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RespawnManager implements Listener {
    private final RavenCore plugin;
    private final BedSpawnRepository beds;
    private final LocationPool locations;
    private final RespawnTransferStore transfers;
    private final RespawnConfig config;
    private final Location defaultSpawn;
    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();
    private final AtomicLong attemptSequence = new AtomicLong();

    public RespawnManager(RavenCore plugin, BedSpawnRepository beds, LocationPool locations,
                          RespawnTransferStore transfers, RespawnConfig config, Location defaultSpawn) {
        this.plugin = plugin;
        this.beds = beds;
        this.locations = locations;
        this.transfers = transfers;
        this.config = config;
        this.defaultSpawn = defaultSpawn.clone();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        event.setRespawnLocation(defaultSpawn());
        long attemptId = beginAttempt(player, true);
        resolveSavedBed(player, attemptId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long attemptId = beginAttempt(player, false);
        SchedulerUtil.runAsync(() -> {
            ConsumeResult result = transfers.consume(player.getUniqueId());
            SchedulerUtil.execute(player, () -> handleIncomingResult(player, attemptId, result));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        attempts.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        attempts.clear();
    }

    private long beginAttempt(Player player, boolean fallbackOnTimeout) {
        long attemptId = attemptSequence.incrementAndGet();
        UUID playerUuid = player.getUniqueId();
        attempts.put(playerUuid, new Attempt(attemptId, fallbackOnTimeout));
        suppressBorders(player);
        SchedulerUtil.runAsyncLater(() -> {
            Attempt current = attempts.get(playerUuid);
            if (current == null || current.id() != attemptId || !player.isOnline()) return;
            SchedulerUtil.execute(player, () -> {
                Attempt active = attempts.get(playerUuid);
                if (active == null || active.id() != attemptId) return;
                if (active.fallbackOnTimeout()) {
                    fallbackToDefault(player, attemptId);
                } else {
                    finishWithoutTeleport(player, attemptId);
                }
            });
        }, config.resolutionTimeoutTicks());
        return attemptId;
    }

    private void handleIncomingResult(Player player, long attemptId, ConsumeResult result) {
        if (!isActive(player, attemptId)) return;
        if (result.status() == ConsumeStatus.NONE || result.status() == ConsumeStatus.UNAVAILABLE) {
            finishWithoutTeleport(player, attemptId);
            return;
        }
        activateFallback(player, attemptId);
        if (result.status() == ConsumeStatus.INVALID || result.payload() == null) {
            plugin.getLogger().warning("[Respawn] Invalid pending respawn payload for " + player.getName());
            fallbackToDefault(player, attemptId);
            return;
        }
        applyIncoming(player, attemptId, result.payload());
    }

    private void resolveSavedBed(Player player, long attemptId) {
        SchedulerUtil.runAsync(() -> {
            Optional<BedSpawnRecord> bed;
            try {
                bed = beds.find(player.getUniqueId());
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to load bed for " + player.getName()
                        + "; preserving its record: " + error.getMessage());
                scheduleForPlayer(player, attemptId, () -> resolveRandom(player, attemptId));
                return;
            }
            scheduleForPlayer(player, attemptId, () -> {
                if (bed.isPresent()) useBed(player, attemptId, bed.get());
                else resolveRandom(player, attemptId);
            });
        });
    }

    private void useBed(Player player, long attemptId, BedSpawnRecord bed) {
        if (!isActive(player, attemptId)) return;
        if (!bed.serverId().equalsIgnoreCase(plugin.getServerId())) {
            if (!transfers.storeBed(bed)) {
                failToDefault(player, attemptId);
                return;
            }
            sendToServer(player, bed.serverId());
            return;
        }
        validateBed(player, attemptId, bed);
    }

    private void validateBed(Player player, long attemptId, BedSpawnRecord bed) {
        BedSpawnLocator.resolveAsync(plugin, bed, result ->
                scheduleForPlayer(player, attemptId, () -> handleBedResult(player, attemptId, bed, result)));
    }

    private void handleBedResult(Player player, long attemptId, BedSpawnRecord bed,
                                 BedSpawnResolution result) {
        if (result.status() == BedSpawnStatus.VALID && result.location() != null) {
            teleportAndFinish(player, attemptId, result.location());
            return;
        }
        if (result.status() == BedSpawnStatus.INVALID) {
            deleteConfirmedStaleBed(bed);
        }
        resolveRandom(player, attemptId);
    }

    private void resolveRandom(Player player, long attemptId) {
        if (!isActive(player, attemptId)) return;
        SchedulerUtil.runAsync(() -> {
            Optional<RtpLocation> location;
            try {
                location = locations.getRandomLocationAcrossClusters(
                        config.clusters(), LocationDimension.OVERWORLD);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to obtain a random location for "
                        + player.getName() + ": " + error.getMessage());
                location = Optional.empty();
            }
            Optional<RtpLocation> selected = location;
            scheduleForPlayer(player, attemptId, () -> {
                if (selected.isEmpty()) {
                    player.sendMessage(ChatFormatter.color(config.noLocationMessage()));
                    fallbackToDefault(player, attemptId);
                    return;
                }
                useRandom(player, attemptId, selected.get());
            });
        });
    }

    private void useRandom(Player player, long attemptId, RtpLocation location) {
        if (!location.serverId().equalsIgnoreCase(plugin.getServerId())) {
            if (!transfers.storeRandom(player.getUniqueId(), location)) {
                failToDefault(player, attemptId);
                return;
            }
            sendToServer(player, location.serverId());
            return;
        }
        World world = Bukkit.getWorld(location.world());
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            plugin.getLogger().warning("[Respawn] Random destination world is unavailable or not an Overworld: "
                    + location.world());
            fallbackToDefault(player, attemptId);
            return;
        }
        teleportAndFinish(player, attemptId, new Location(world, location.x(), location.y(), location.z(),
                location.yaw(), location.pitch()));
    }

    private void applyIncoming(Player player, long attemptId, RespawnTransferStore.Payload payload) {
        if (!payload.serverId().equalsIgnoreCase(plugin.getServerId())) {
            plugin.getLogger().warning("[Respawn] Received destination for " + payload.serverId()
                    + " on " + plugin.getServerId());
            fallbackToDefault(player, attemptId);
            return;
        }
        if (payload.isBed()) {
            BedSpawnRecord bed = new BedSpawnRecord(player.getUniqueId(), payload.serverId(), payload.world(),
                    (int) payload.x(), (int) payload.y(), (int) payload.z());
            validateBed(player, attemptId, bed);
            return;
        }
        useRandom(player, attemptId, new RtpLocation(payload.serverId(), LocationDimension.OVERWORLD,
                payload.world(), payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch()));
    }

    private void sendToServer(Player player, String serverId) {
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(serverId);
        plugin.getNetworkEventBus().publish(event);
    }

    private void deleteConfirmedStaleBed(BedSpawnRecord bed) {
        SchedulerUtil.runAsync(() -> {
            try {
                beds.deleteAt(bed.serverId(), bed.world(), bed.x(), bed.y(), bed.z());
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to delete confirmed stale bed: "
                        + error.getMessage());
            }
        });
    }

    private void teleportAndFinish(Player player, long attemptId, Location location) {
        if (!claim(player, attemptId)) return;
        player.teleportAsync(location).whenComplete((success, error) -> {
            if (error == null && Boolean.TRUE.equals(success)) {
                releaseBorders(player);
                return;
            }
            SchedulerUtil.execute(player, () -> teleportToDefaultAndRelease(player));
        });
    }

    private void fallbackToDefault(Player player, long attemptId) {
        if (!claim(player, attemptId)) return;
        teleportToDefaultAndRelease(player);
    }

    private void teleportToDefaultAndRelease(Player player) {
        player.teleportAsync(defaultSpawn()).whenComplete((success, error) -> releaseBorders(player));
    }

    private void failToDefault(Player player, long attemptId) {
        player.sendMessage(ChatFormatter.color(config.transferFailedMessage()));
        fallbackToDefault(player, attemptId);
    }

    private void finishWithoutTeleport(Player player, long attemptId) {
        if (!claim(player, attemptId)) return;
        releaseBorders(player);
    }

    private void activateFallback(Player player, long attemptId) {
        attempts.computeIfPresent(player.getUniqueId(), (uuid, current) ->
                current.id() == attemptId ? new Attempt(attemptId, true) : current);
    }

    private void scheduleForPlayer(Player player, long attemptId, Runnable action) {
        SchedulerUtil.execute(player, () -> {
            if (player.isOnline() && isActive(player, attemptId)) action.run();
        });
    }

    private boolean isActive(Player player, long attemptId) {
        Attempt attempt = attempts.get(player.getUniqueId());
        return attempt != null && attempt.id() == attemptId;
    }

    private boolean claim(Player player, long attemptId) {
        Attempt attempt = attempts.get(player.getUniqueId());
        return attempt != null && attempt.id() == attemptId
                && attempts.remove(player.getUniqueId(), attempt);
    }

    private Location defaultSpawn() {
        return defaultSpawn.clone();
    }

    private void suppressBorders(Player player) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().suppressTransfers(player.getUniqueId(),
                    config.resolutionTimeoutTicks() * 50L);
        }
    }

    private void releaseBorders(Player player) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().releaseTransfers(player.getUniqueId());
        }
    }

    private record Attempt(long id, boolean fallbackOnTimeout) {
    }
}
