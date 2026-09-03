package com.kammoun.raven.respawn.manager;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.endportal.listener.EndPortalRespawnListener;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.location.NetworkLocation;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.respawn.bed.BedSpawnLocator;
import com.kammoun.raven.respawn.bed.BedSpawnLocator.BedSpawnResolution;
import com.kammoun.raven.respawn.bed.BedSpawnLocator.BedSpawnStatus;
import com.kammoun.raven.respawn.bed.BedSpawnRecord;
import com.kammoun.raven.respawn.config.RespawnConfig;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import com.kammoun.raven.respawn.transfer.RespawnTransferStore;
import com.kammoun.raven.respawn.transfer.RespawnTransferStore.ConsumeResult;
import com.kammoun.raven.respawn.transfer.RespawnTransferStore.ConsumeStatus;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RespawnManager implements Listener {
    private static final long MIN_REMOTE_TRANSFER_SUPPRESSION_MILLIS = 30_000L;
    private static final long DEATH_PLAN_TTL_MILLIS = 20_000L;
    private static final long DEATH_PLAN_VISIBLE_WAIT_TICKS = 40L;
    private static final long MISSED_RESPAWN_RECOVERY_START_TICKS = 20L;
    private static final long MISSED_RESPAWN_RECOVERY_POLL_TICKS = 2L;
    private static final long MISSED_RESPAWN_RECOVERY_TIMEOUT_TICKS = 80L;
    private static final int VISUAL_MASK_TICKS = 35;
    private static final long JOIN_FREEZE_FAILSAFE_TICKS = 80L;

    private final RavenCore plugin;
    private final BedSpawnRepository beds;
    private final LocationRepository spawnLocations;
    private final RespawnTransferStore transfers;
    private final RespawnConfig config;
    private final BungeeHelper bungeeHelper;
    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, DeathPlanEntry> deathPlans = new ConcurrentHashMap<>();
    private final Map<UUID, RecoveryEntry> pendingRecoveries = new ConcurrentHashMap<>();
    private final Map<UUID, FreezeState> frozen = new ConcurrentHashMap<>();
    private final AtomicLong attemptSequence = new AtomicLong();
    private volatile SchedulerUtil.Task recoveryTicker;
    private volatile Location localHoldingSpawn;

    public RespawnManager(RavenCore plugin, BedSpawnRepository beds,
                          LocationRepository spawnLocations, RespawnTransferStore transfers,
                          RespawnConfig config) {
        this.plugin = plugin;
        this.beds = beds;
        this.spawnLocations = spawnLocations;
        this.transfers = transfers;
        this.config = config;
        this.bungeeHelper = new BungeeHelper(plugin);
        refreshLocalHoldingSpawn();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        deathPlans.put(player.getUniqueId(), new DeathPlanEntry(
                precomputeDeathPlan(player.getUniqueId(), player.getName()),
                System.currentTimeMillis() + DEATH_PLAN_TTL_MILLIS));
        scheduleRespawnRecovery(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPearlStasisModule() != null && plugin.getPearlStasisModule().hasPendingStasis(player.getUniqueId())) {
            return;
        }
        // Leaving the End arrives here as a respawn. It is not a death, so the End
        // portal module owns the destination — resolving a bed for it would drag the
        // player somewhere they never asked to go.
        if (plugin.getEndPortalModule() != null && EndPortalRespawnListener.isEndPortalExit(event)) {
            return;
        }
        DeathPlanEntry entry = deathPlans.remove(player.getUniqueId());
        if (entry != null && !entry.expired() && entry.future().isDone()) {
            DeathPlan completed = completedDeathPlan(player, entry.future());
            Location seamless = completed.localRespawnLocation(plugin);
            if (seamless != null) {
                event.setRespawnLocation(seamless);
                return;
            }
            if (completed.kind() == DeathPlanKind.NONE && isVanillaBedRespawn(event)) {
                return;
            }
            event.setRespawnLocation(holdingSpawn(player));
            long attemptId = beginAttempt(player, true);
            useDeathPlan(player, attemptId, completed);
            return;
        }

        if (entry == null && isVanillaBedRespawn(event)) {
            return;
        }

        event.setRespawnLocation(holdingSpawn(player));
        long attemptId = beginAttempt(player, true);
        applyVisualMask(player);
        if (entry == null || entry.expired()) {
            resolveSavedBed(player, attemptId);
            return;
        }

        CompletableFuture<DeathPlan> future = entry.future();
        future.whenComplete((result, error) -> scheduleForPlayer(player, attemptId, () -> {
            if (error instanceof CancellationException) return;
            if (error != null) {
                plugin.getLogger().warning("[Respawn] Failed to precompute respawn for " + player.getName()
                        + ": " + error.getMessage());
                resolveSavedBed(player, attemptId);
                return;
            }
            useDeathPlan(player, attemptId, result);
        }));
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline() || !isActive(player, attemptId) || future.isDone()) return;
            future.cancel(false);
            resolveSavedBed(player, attemptId);
        }), DEATH_PLAN_VISIBLE_WAIT_TICKS);
    }

    private DeathPlan completedDeathPlan(Player player, CompletableFuture<DeathPlan> future) {
        try {
            return future.getNow(DeathPlan.none());
        } catch (RuntimeException error) {
            plugin.getLogger().warning("[Respawn] Completed death plan failed for " + player.getName()
                    + ": " + error.getMessage());
            return DeathPlan.none();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPearlStasisModule() != null && plugin.getPearlStasisModule().hasPendingStasis(player.getUniqueId())) {
            return;
        }
        long attemptId = beginAttempt(player, false);
        SchedulerUtil.runAsync(() -> {
            ConsumeResult result = transfers.peek(player.getUniqueId());
            SchedulerUtil.runLater(() -> SchedulerUtil.execute(player,
                    () -> handleIncomingResult(player, attemptId, result)), 1L);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        attempts.remove(playerUuid);
        deathPlans.remove(playerUuid);
        pendingRecoveries.remove(playerUuid);
    }

    public void stop() {
        attempts.clear();
        deathPlans.clear();
        pendingRecoveries.clear();
        frozen.clear();
        stopRecoveryTicker();
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
            transfers.delete(player.getUniqueId());
            fallbackToDefault(player, attemptId);
            return;
        }
        if (!result.payload().serverId().equalsIgnoreCase(plugin.getServerId())) {
            plugin.getLogger().info("[Respawn] Pending respawn for " + player.getName()
                    + " belongs to " + result.payload().serverId() + ", not " + plugin.getServerId() + ".");
            finishWithoutTeleport(player, attemptId);
            return;
        }
        plugin.getLogger().info("[Respawn] Finalizing pending respawn for " + player.getName()
                + " on " + plugin.getServerId() + " at " + result.payload().world()
                + " " + result.payload().x() + " " + result.payload().y() + " " + result.payload().z() + ".");
        freezePlayer(player);
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
                if (bed.isPresent()) {
                    useBed(player, attemptId, bed.get());
                } else {
                    resolveRandom(player, attemptId);
                }
            });
        });
    }

    private void useBed(Player player, long attemptId, BedSpawnRecord bed) {
        if (!isActive(player, attemptId)) return;
        if (!bed.serverId().equalsIgnoreCase(plugin.getServerId())) {
            plugin.getLogger().info("[Respawn] Preparing remote bed transfer for " + player.getName()
                    + " to " + bed.serverId() + "/" + bed.world() + " "
                    + bed.x() + " " + bed.y() + " " + bed.z() + ".");
            if (!transfers.storeBed(bed)) {
                failToDefault(player, attemptId);
                return;
            }
            beginRemoteTransfer(player, attemptId, bed.serverId());
            return;
        }
        plugin.getLogger().info("[Respawn] Validating local bed for " + player.getName()
                + " at " + bed.serverId() + "/" + bed.world() + " "
                + bed.x() + " " + bed.y() + " " + bed.z() + ".");
        validateBed(player, attemptId, bed);
    }

    private void useDeathPlan(Player player, long attemptId, DeathPlan plan) {
        if (plan == null || plan.kind() == DeathPlanKind.NONE) {
            resolveSavedBed(player, attemptId);
            return;
        }
        switch (plan.kind()) {
            case LOCAL_LOCATION -> teleportAndFinish(player, attemptId, plan.location());
            case BED -> useBed(player, attemptId, plan.bed());
            case RTP -> useRtpLocation(player, attemptId, plan.rtp());
            case DESTINATION -> useDestination(player, attemptId, plan.destination());
            case NONE -> resolveSavedBed(player, attemptId);
        }
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
            Optional<RtpLocation> selected = randomFallbackLocation();
            scheduleForPlayer(player, attemptId, () -> {
                if (selected.isPresent()) {
                    useRtpLocation(player, attemptId, selected.get());
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
                spawn = selectSpawnDestination();
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

    private CompletableFuture<DeathPlan> precomputeDeathPlan(UUID playerUuid, String playerName) {
        CompletableFuture<DeathPlan> future = new CompletableFuture<>();
        SchedulerUtil.runAsync(() -> {
            try {
                Optional<BedSpawnRecord> bed = beds.find(playerUuid);
                if (bed.isPresent()) {
                    BedSpawnRecord record = bed.get();
                    if (!record.serverId().equalsIgnoreCase(plugin.getServerId())) {
                        future.complete(DeathPlan.bed(record));
                        return;
                    }
                    resolveLocalBedPlan(playerName, record, future);
                    return;
                }

                future.complete(randomFallbackPlan());
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().warning("[Respawn] Failed to pre-resolve respawn for " + playerName
                        + ": " + error.getMessage());
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private void resolveLocalBedPlan(String playerName, BedSpawnRecord bed,
                                     CompletableFuture<DeathPlan> future) {
        BedSpawnLocator.resolveAsync(plugin, bed, result -> {
            if (result.status() == BedSpawnStatus.VALID && result.location() != null) {
                future.complete(DeathPlan.local(result.location()));
                return;
            }
            if (result.status() == BedSpawnStatus.INVALID) {
                deleteConfirmedStaleBed(bed);
            }
            SchedulerUtil.runAsync(() -> {
                try {
                    future.complete(randomFallbackPlan());
                } catch (SQLException | RuntimeException error) {
                    plugin.getLogger().warning("[Respawn] Failed to precompute spawn fallback for "
                            + playerName + ": " + error.getMessage());
                    future.completeExceptionally(error);
                }
            });
        });
    }

    private Optional<RtpLocation> randomFallbackLocation() {
        if (plugin.getLocationPoolModule() == null || plugin.getLocationPoolModule().getPool() == null) {
            return Optional.empty();
        }
        LocationPool pool = plugin.getLocationPoolModule().getPool();
        if (!config.clusters().isEmpty()) {
            return pool.getRandomLocationAcrossClusters(config.clusters(), LocationDimension.OVERWORLD);
        }
        return pool.getRandomLocationGlobal(LocationDimension.OVERWORLD);
    }

    private DeathPlan randomFallbackPlan() throws SQLException {
        Optional<RtpLocation> random = randomFallbackLocation();
        if (random.isPresent()) {
            return DeathPlan.rtp(random.get());
        }
        Optional<NetworkLocation> spawn = selectSpawnDestination();
        return spawn.map(location -> DeathPlan.destination(RespawnDestination.spawn(location)))
                .orElseGet(DeathPlan::none);
    }

    private Optional<NetworkLocation> selectSpawnDestination() throws SQLException {
        String preferred = config.spawnServer();
        if (preferred != null && !preferred.isBlank()) {
            return spawnLocations.findSpawn(preferred);
        }

        List<NetworkLocation> spawns = spawnLocations.findAll().stream()
                .filter(location -> location.getType() == LocationType.SPAWN)
                .filter(NetworkLocation::isEnabled)
                .toList();
        if (spawns.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Long> online = onlineByServer();
        return spawns.stream()
                .filter(location -> location.getMaxPlayers() <= 0
                        || online.getOrDefault(location.getServerName(), 0L) < location.getMaxPlayers())
                .min(Comparator
                        .comparingLong((NetworkLocation location) ->
                                online.getOrDefault(location.getServerName(), 0L))
                        .thenComparing(NetworkLocation::getServerName));
    }

    private Map<String, Long> onlineByServer() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        if (plugin.getNetworkPlayerManager() == null) {
            counts.put(plugin.getServerId(), (long) Bukkit.getOnlinePlayers().size());
            return counts;
        }
        for (NetworkPlayerInfo info : plugin.getNetworkPlayerManager().getOnlineNetworkPlayers()) {
            if (info.getServerId() == null || info.getServerId().isBlank()) continue;
            counts.merge(info.getServerId(), 1L, Long::sum);
        }
        return counts;
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

    private void useRtpLocation(Player player, long attemptId, RtpLocation target) {
        if (target.serverId().equalsIgnoreCase(plugin.getServerId())) {
            World world = Bukkit.getWorld(target.world());
            if (world == null) {
                plugin.getLogger().warning("[Respawn] RTP destination world is unavailable: " + target.world());
                fallbackToDefault(player, attemptId);
                return;
            }
            teleportAndFinish(player, attemptId, new Location(world, target.x(), target.y(), target.z(),
                    target.yaw(), target.pitch()));
            return;
        }
        if (!transfers.storeCoordinate(player.getUniqueId(), target.serverId(), target.world(),
                target.x(), target.y(), target.z(), target.yaw(), target.pitch())) {
            failToDefault(player, attemptId);
            return;
        }
        beginRemoteTransfer(player, attemptId, target.serverId());
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
            validateIncomingBed(player, attemptId, bed);
            return;
        }
        useIncomingDestination(player, attemptId, new RespawnDestination(payload.serverId(), payload.world(),
                payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch()));
    }

    private void validateIncomingBed(Player player, long attemptId, BedSpawnRecord bed) {
        BedSpawnLocator.resolveAsync(plugin, bed, result ->
                scheduleForPlayer(player, attemptId, () -> handleIncomingBedResult(player, attemptId, bed, result)));
    }

    private void handleIncomingBedResult(Player player, long attemptId, BedSpawnRecord bed,
                                         BedSpawnResolution result) {
        if (result.status() == BedSpawnStatus.VALID && result.location() != null) {
            teleportAndFinish(player, attemptId, result.location(), () -> transfers.delete(player.getUniqueId()));
            return;
        }
        if (result.status() == BedSpawnStatus.INVALID) {
            deleteConfirmedStaleBed(bed);
        }
        transfers.delete(player.getUniqueId());
        resolveRandom(player, attemptId);
    }

    private void useIncomingDestination(Player player, long attemptId, RespawnDestination destination) {
        if (!destination.serverId().equalsIgnoreCase(plugin.getServerId())) {
            finishWithoutTeleport(player, attemptId);
            return;
        }
        World world = Bukkit.getWorld(destination.world());
        if (world == null) {
            plugin.getLogger().warning("[Respawn] Destination world is unavailable: " + destination.world());
            fallbackToDefault(player, attemptId);
            return;
        }
        teleportAndFinish(player, attemptId, new Location(world, destination.x(), destination.y(), destination.z(),
                destination.yaw(), destination.pitch()), () -> transfers.delete(player.getUniqueId()));
    }

    private void sendToServer(Player player, String serverId) {
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(serverId);
        plugin.getNetworkEventBus().publish(event);
        plugin.getLogger().info("[Respawn] Sending " + player.getName() + " from "
                + plugin.getServerId() + " to " + serverId + ".");
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) {
                plugin.getLogger().warning("[Respawn] Could not connect " + player.getName()
                        + " to " + serverId + ": player is offline.");
                return;
            }
            plugin.getLogger().info("[Respawn] Proxy connect request for " + player.getName()
                    + " -> " + serverId + ".");
            bungeeHelper.connectToServer(player, serverId);
        }), 2L);
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
        teleportAndFinish(player, attemptId, location, null);
    }

    private void teleportAndFinish(Player player, long attemptId, Location location, Runnable afterSuccess) {
        if (!claim(player, attemptId)) return;
        player.teleportAsync(location).whenComplete((success, error) -> {
            if (error == null && Boolean.TRUE.equals(success)) {
                if (afterSuccess != null) afterSuccess.run();
                unfreezePlayer(player);
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
        player.teleportAsync(holdingSpawn(player)).whenComplete((success, error) -> {
            unfreezePlayer(player);
            releaseBorders(player);
        });
    }

    private void applyVisualMask(Player player) {
        SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, VISUAL_MASK_TICKS, 0,
                    false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, VISUAL_MASK_TICKS, 0,
                    false, false, false));
        });
    }

    private void freezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozen.containsKey(uuid)) return;
        frozen.put(uuid, new FreezeState(player.getWalkSpeed(), player.getFlySpeed(),
                player.isFlying(), player.getAllowFlight(), player.isInvulnerable()));
        try {
            player.setInvulnerable(true);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setWalkSpeed(0.0f);
            player.setFlySpeed(0.0f);
        } catch (RuntimeException ignored) {
        }
        applyVisualMask(player);
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> unfreezePlayer(player)),
                JOIN_FREEZE_FAILSAFE_TICKS);
    }

    private void unfreezePlayer(Player player) {
        FreezeState state = frozen.remove(player.getUniqueId());
        if (state == null || !player.isOnline()) return;
        try {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.setWalkSpeed(state.walkSpeed());
            player.setFlySpeed(state.flySpeed());
            player.setAllowFlight(state.allowFlight());
            player.setFlying(state.flying() && state.allowFlight());
            player.setInvulnerable(state.invulnerable());
        } catch (RuntimeException ignored) {
        }
    }

    private void failToDefault(Player player, long attemptId) {
        player.sendMessage(ChatFormatter.color(config.transferFailedMessage()));
        fallbackToDefault(player, attemptId);
    }

    private void finishWithoutTeleport(Player player, long attemptId) {
        if (!claim(player, attemptId)) return;
        unfreezePlayer(player);
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

    private static boolean isVanillaBedRespawn(PlayerRespawnEvent event) {
        if (callBoolean(event, "isBedSpawn")) {
            return true;
        }
        Location location = event.getRespawnLocation();
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return BedSpawnLocator.canonicalBed(world.getBlockAt(x, y, z)).isPresent()
                || BedSpawnLocator.canonicalBed(world.getBlockAt(x, y - 1, z)).isPresent();
    }

    private static boolean callBoolean(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void scheduleRespawnRecovery(Player player) {
        pendingRecoveries.put(player.getUniqueId(), new RecoveryEntry(
                System.currentTimeMillis() + (MISSED_RESPAWN_RECOVERY_START_TICKS * 50L),
                System.currentTimeMillis() + ((MISSED_RESPAWN_RECOVERY_START_TICKS
                        + MISSED_RESPAWN_RECOVERY_TIMEOUT_TICKS) * 50L)));
        ensureRecoveryTicker();
    }

    private synchronized void ensureRecoveryTicker() {
        if (recoveryTicker != null) return;
        recoveryTicker = SchedulerUtil.runTimer(this::tickRespawnRecoveries,
                MISSED_RESPAWN_RECOVERY_POLL_TICKS, MISSED_RESPAWN_RECOVERY_POLL_TICKS);
    }

    private synchronized void stopRecoveryTicker() {
        if (recoveryTicker == null) return;
        recoveryTicker.cancel();
        recoveryTicker = null;
    }

    private void tickRespawnRecoveries() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, RecoveryEntry> entry : pendingRecoveries.entrySet()) {
            UUID uuid = entry.getKey();
            RecoveryEntry recovery = entry.getValue();
            if (now < recovery.startsAtMillis()) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !deathPlans.containsKey(uuid) || now >= recovery.expiresAtMillis()) {
                pendingRecoveries.remove(uuid, recovery);
                continue;
            }
            if (!player.isDead() && pendingRecoveries.remove(uuid, recovery)) {
                long elapsedTicks = Math.max(MISSED_RESPAWN_RECOVERY_START_TICKS,
                        (now - recovery.startsAtMillis()) / 50L + MISSED_RESPAWN_RECOVERY_START_TICKS);
                recoverMissedRespawnEvent(player, elapsedTicks);
            }
        }
        if (pendingRecoveries.isEmpty()) {
            stopRecoveryTicker();
        }
    }

    private void recoverMissedRespawnEvent(Player player, long delayTicks) {
        DeathPlanEntry entry = deathPlans.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        plugin.getLogger().info("[Respawn] PlayerRespawnEvent did not fire for " + player.getName()
                + "; recovering from death probe +" + delayTicks + "t.");
        long attemptId = beginAttempt(player, true);
        applyVisualMask(player);
        if (entry.expired()) {
            resolveSavedBed(player, attemptId);
            return;
        }
        CompletableFuture<DeathPlan> future = entry.future();
        if (future.isDone()) {
            useDeathPlan(player, attemptId, completedDeathPlan(player, future));
            return;
        }
        future.whenComplete((result, error) -> scheduleForPlayer(player, attemptId, () -> {
            if (error instanceof CancellationException) return;
            if (error != null) {
                plugin.getLogger().warning("[Respawn] Failed to recover precomputed respawn for "
                        + player.getName() + ": " + error.getMessage());
                resolveSavedBed(player, attemptId);
                return;
            }
            useDeathPlan(player, attemptId, result);
        }));
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline() || !isActive(player, attemptId) || future.isDone()) return;
            future.cancel(false);
            resolveSavedBed(player, attemptId);
        }), DEATH_PLAN_VISIBLE_WAIT_TICKS);
    }

    private record Attempt(long id, boolean fallbackOnTimeout) {
    }

    private record RecoveryEntry(long startsAtMillis, long expiresAtMillis) {
    }

    private record FreezeState(float walkSpeed, float flySpeed, boolean flying,
                               boolean allowFlight, boolean invulnerable) {
    }

    private record DeathPlanEntry(CompletableFuture<DeathPlan> future, long expiresAtMillis) {
        private boolean expired() {
            return expiresAtMillis <= System.currentTimeMillis();
        }
    }

    private enum DeathPlanKind {
        NONE,
        LOCAL_LOCATION,
        BED,
        RTP,
        DESTINATION
    }

    private record DeathPlan(DeathPlanKind kind, Location location, BedSpawnRecord bed,
                             RtpLocation rtp, RespawnDestination destination) {
        private static DeathPlan none() {
            return new DeathPlan(DeathPlanKind.NONE, null, null, null, null);
        }

        private static DeathPlan local(Location location) {
            return new DeathPlan(DeathPlanKind.LOCAL_LOCATION, location, null, null, null);
        }

        private static DeathPlan bed(BedSpawnRecord bed) {
            return new DeathPlan(DeathPlanKind.BED, null, bed, null, null);
        }

        private static DeathPlan rtp(RtpLocation location) {
            return new DeathPlan(DeathPlanKind.RTP, null, null, location, null);
        }

        private static DeathPlan destination(RespawnDestination destination) {
            return new DeathPlan(DeathPlanKind.DESTINATION, null, null, null, destination);
        }

        private Location localRespawnLocation(RavenCore plugin) {
            if (kind == DeathPlanKind.LOCAL_LOCATION && location != null) {
                return location.clone();
            }
            if (kind == DeathPlanKind.RTP && rtp != null
                    && rtp.serverId().equalsIgnoreCase(plugin.getServerId())) {
                World world = Bukkit.getWorld(rtp.world());
                if (world == null) return null;
                return new Location(world, rtp.x(), rtp.y(), rtp.z(), rtp.yaw(), rtp.pitch());
            }
            if (kind == DeathPlanKind.DESTINATION && destination != null
                    && destination.serverId().equalsIgnoreCase(plugin.getServerId())) {
                World world = Bukkit.getWorld(destination.world());
                if (world == null) return null;
                return new Location(world, destination.x(), destination.y(), destination.z(),
                        destination.yaw(), destination.pitch());
            }
            return null;
        }
    }

    private record RespawnDestination(String serverId, String world, double x, double y, double z,
                                      float yaw, float pitch) {
        private static RespawnDestination spawn(NetworkLocation spawn) {
            return new RespawnDestination(spawn.getServerName(), spawn.getWorld(), spawn.getX(), spawn.getY(),
                    spawn.getZ(), spawn.getYaw(), spawn.getPitch());
        }
    }
}
