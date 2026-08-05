package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.home.db.HomeRepository;
import com.kammoun.raven.home.model.HomeRecord;
import com.kammoun.raven.library.location.NetworkLocation;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.respawn.BedSpawnLocator.BedSpawnResolution;
import com.kammoun.raven.respawn.BedSpawnLocator.BedSpawnStatus;
import com.kammoun.raven.respawn.RespawnTransferStore.ConsumeResult;
import com.kammoun.raven.respawn.RespawnTransferStore.ConsumeStatus;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import com.kammoun.raven.spawn.db.LocationRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RespawnManager implements Listener {
    private static final long MIN_REMOTE_TRANSFER_SUPPRESSION_MILLIS = 30_000L;

    private final RavenCore plugin;
    private final BedSpawnRepository beds;
    private final HomeRepository homes;
    private final LocationRepository spawnLocations;
    private final RespawnTransferStore transfers;
    private final RespawnConfig config;
    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Optional<PreparedRespawn>>> preparedRespawns = new ConcurrentHashMap<>();
    private final AtomicLong attemptSequence = new AtomicLong();
    private volatile Location localHoldingSpawn;

    public RespawnManager(RavenCore plugin, BedSpawnRepository beds, HomeRepository homes,
                          LocationRepository spawnLocations, RespawnTransferStore transfers,
                          RespawnConfig config) {
        this.plugin = plugin;
        this.beds = beds;
        this.homes = homes;
        this.spawnLocations = spawnLocations;
        this.transfers = transfers;
        this.config = config;
        refreshLocalHoldingSpawn();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        preparedRespawns.put(player.getUniqueId(),
                resolvePreparedRespawn(player.getUniqueId(), player.getName()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        event.setRespawnLocation(holdingSpawn(player));
        long attemptId = beginAttempt(player, true);
        CompletableFuture<Optional<PreparedRespawn>> prepared = preparedRespawns.remove(player.getUniqueId());
        if (prepared == null) {
            resolveSavedBed(player, attemptId);
            return;
        }
        prepared.whenComplete((result, error) -> scheduleForPlayer(player, attemptId, () -> {
            if (error != null) {
                plugin.getLogger().warning("[Respawn] Failed to prepare respawn for " + player.getName()
                        + ": " + error.getMessage());
                resolveSavedBed(player, attemptId);
                return;
            }
            if (result.isPresent()) {
                usePreparedRespawn(player, attemptId, result.get());
                return;
            }
            useConfiguredSpawn(player, attemptId);
        }));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long attemptId = beginAttempt(player, false);
        SchedulerUtil.runAsync(() -> {
            ConsumeResult result = transfers.consume(player.getUniqueId());
            SchedulerUtil.runLater(() -> SchedulerUtil.execute(player,
                    () -> handleIncomingResult(player, attemptId, result)), 1L);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        attempts.remove(playerUuid);
        preparedRespawns.remove(playerUuid);
    }

    public void stop() {
        attempts.clear();
        preparedRespawns.clear();
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
                scheduleForPlayer(player, attemptId, () -> resolveFirstHome(player, attemptId));
                return;
            }
            scheduleForPlayer(player, attemptId, () -> {
                if (bed.isPresent()) useBed(player, attemptId, bed.get());
                else resolveFirstHome(player, attemptId);
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
            beginRemoteTransfer(player, attemptId, bed.serverId());
            return;
        }
        validateBed(player, attemptId, bed);
    }

    private void usePreparedRespawn(Player player, long attemptId, PreparedRespawn prepared) {
        if (prepared.bed() != null) {
            useBed(player, attemptId, prepared.bed());
            return;
        }
        useDestination(player, attemptId, prepared.destination());
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
        resolveFirstHome(player, attemptId);
    }

    private void resolveFirstHome(Player player, long attemptId) {
        if (!isActive(player, attemptId)) return;
        SchedulerUtil.runAsync(() -> {
            Optional<HomeRecord> home;
            try {
                home = homes.findFirst(player.getUniqueId());
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to load first home for " + player.getName()
                        + ": " + error.getMessage());
                home = Optional.empty();
            }
            Optional<HomeRecord> selected = home;
            scheduleForPlayer(player, attemptId, () -> {
                if (selected.isPresent()) {
                    useDestination(player, attemptId, RespawnDestination.home(selected.get()));
                    return;
                }
                resolveSpawn(player, attemptId);
            });
        });
    }

    private void resolveSpawn(Player player, long attemptId) {
        if (!isActive(player, attemptId)) return;
        SchedulerUtil.runAsync(() -> {
            Optional<NetworkLocation> spawn;
            try {
                spawn = spawnLocations.findSpawn(config.spawnServer());
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to load spawn location for " + player.getName()
                        + ": " + error.getMessage());
                spawn = Optional.empty();
            }
            Optional<NetworkLocation> selected = spawn;
            scheduleForPlayer(player, attemptId, () -> {
                if (selected.isPresent()) {
                    useDestination(player, attemptId, RespawnDestination.spawn(selected.get()));
                    return;
                }
                useConfiguredSpawn(player, attemptId);
            });
        });
    }

    private void useConfiguredSpawn(Player player, long attemptId) {
        RespawnConfig.DefaultSpawn spawn = config.defaultSpawn();
        String server = spawn.server() == null || spawn.server().isBlank()
                ? plugin.getServerId()
                : spawn.server();
        if (server.equalsIgnoreCase(plugin.getServerId())) {
            teleportAndFinish(player, attemptId, holdingSpawn(player));
            return;
        }
        useDestination(player, attemptId, new RespawnDestination(server, spawn.world(),
                spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()));
    }

    private CompletableFuture<Optional<PreparedRespawn>> resolvePreparedRespawn(UUID playerUuid, String playerName) {
        CompletableFuture<Optional<PreparedRespawn>> future = new CompletableFuture<>();
        SchedulerUtil.runAsync(() -> {
            try {
                Optional<BedSpawnRecord> bed = beds.find(playerUuid);
                if (bed.isPresent()) {
                    future.complete(Optional.of(PreparedRespawn.bed(bed.get())));
                    return;
                }

                Optional<HomeRecord> home = homes.findFirst(playerUuid);
                if (home.isPresent()) {
                    future.complete(Optional.of(PreparedRespawn.destination(RespawnDestination.home(home.get()))));
                    return;
                }

                Optional<NetworkLocation> spawn = spawnLocations.findSpawn(config.spawnServer());
                future.complete(spawn.map(location ->
                        PreparedRespawn.destination(RespawnDestination.spawn(location))));
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to pre-resolve respawn for " + playerName
                        + ": " + error.getMessage());
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private void useDestination(Player player, long attemptId, RespawnDestination destination) {
        if (!destination.serverId().equalsIgnoreCase(plugin.getServerId())) {
            if (!transfers.storeCoordinate(player.getUniqueId(), destination.serverId(), destination.world(),
                    destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch())) {
                failToDefault(player, attemptId);
                return;
            }
            beginRemoteTransfer(player, attemptId, destination.serverId());
            return;
        }
        World world = Bukkit.getWorld(destination.world());
        if (world == null) {
            plugin.getLogger().warning("[Respawn] Destination world is unavailable: " + destination.world());
            fallbackToDefault(player, attemptId);
            return;
        }
        teleportAndFinish(player, attemptId, new Location(world, destination.x(), destination.y(), destination.z(),
                destination.yaw(), destination.pitch()));
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
        useDestination(player, attemptId, new RespawnDestination(payload.serverId(), payload.world(),
                payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch()));
    }

    private void sendToServer(Player player, String serverId) {
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(serverId);
        plugin.getNetworkEventBus().publish(event);
    }

    private void beginRemoteTransfer(Player player, long attemptId, String serverId) {
        if (!claim(player, attemptId)) return;
        suppressBorders(player, remoteTransferSuppressionMillis());
        if (plugin.getCombatModule() != null && plugin.getCombatModule().getService() != null) {
            plugin.getCombatModule().getService().remove(player.getUniqueId(), false, true);
        }
        sendToServer(player, serverId);
    }

    private long remoteTransferSuppressionMillis() {
        return Math.max(MIN_REMOTE_TRANSFER_SUPPRESSION_MILLIS, config.resolutionTimeoutTicks() * 50L);
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
        player.teleportAsync(holdingSpawn(player)).whenComplete((success, error) -> releaseBorders(player));
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

    private Location holdingSpawn(Player player) {
        Location cached = localHoldingSpawn;
        if (cached != null && cached.getWorld() != null) {
            return cached.clone();
        }
        Location spawn = player.getWorld().getSpawnLocation().clone();
        spawn.add(0.5D, 0.0D, 0.5D);
        return spawn;
    }

    private void refreshLocalHoldingSpawn() {
        SchedulerUtil.runAsync(() -> {
            try {
                Optional<NetworkLocation> spawn = spawnLocations.findSpawn(plugin.getServerId());
                if (spawn.isEmpty()) {
                    return;
                }
                NetworkLocation location = spawn.get();
                SchedulerUtil.run(() -> {
                    World world = Bukkit.getWorld(location.getWorld());
                    if (world != null) {
                        localHoldingSpawn = new Location(world, location.getX(), location.getY(), location.getZ(),
                                location.getYaw(), location.getPitch());
                    }
                });
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to cache local holding spawn: "
                        + error.getMessage());
            }
        });
    }

    private void suppressBorders(Player player) {
        suppressBorders(player, config.resolutionTimeoutTicks() * 50L);
    }

    private void suppressBorders(Player player, long durationMillis) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().suppressTransfers(player.getUniqueId(), durationMillis);
        }
    }

    private void releaseBorders(Player player) {
        if (plugin.getBorderModule() != null) {
            plugin.getBorderModule().releaseTransfers(player.getUniqueId());
        }
    }

    private record Attempt(long id, boolean fallbackOnTimeout) {
    }

    private record PreparedRespawn(BedSpawnRecord bed, RespawnDestination destination) {
        private static PreparedRespawn bed(BedSpawnRecord bed) {
            return new PreparedRespawn(bed, null);
        }

        private static PreparedRespawn destination(RespawnDestination destination) {
            return new PreparedRespawn(null, destination);
        }
    }

    private record RespawnDestination(String serverId, String world, double x, double y, double z,
                                      float yaw, float pitch) {
        private static RespawnDestination home(HomeRecord home) {
            return new RespawnDestination(home.serverId(), home.world(), home.x(), home.y(), home.z(),
                    home.yaw(), home.pitch());
        }

        private static RespawnDestination spawn(NetworkLocation spawn) {
            return new RespawnDestination(spawn.getServerName(), spawn.getWorld(), spawn.getX(), spawn.getY(),
                    spawn.getZ(), spawn.getYaw(), spawn.getPitch());
        }
    }
}
