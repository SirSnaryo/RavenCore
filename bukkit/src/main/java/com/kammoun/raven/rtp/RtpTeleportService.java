package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.locationPool.redis.RedisLocationPool;
import com.kammoun.raven.rtp.config.RtpConfig;
import com.kammoun.raven.rtp.config.RtpMessage;
import com.kammoun.raven.rtp.config.RtpMessages;
import com.kammoun.raven.tpa.transfer.TeleportTransferStore;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class RtpTeleportService {
    private final RavenCore plugin;
    private final LocationPool pool;
    private final RtpConfig config;
    private final RtpMessages messages;
    private final RtpSounds sounds;
    private final RtpCooldownStore cooldowns;
    private final RtpWarmupManager warmups;
    private final TeleportTransferStore transfers;
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();

    public RtpTeleportService(RavenCore plugin, LocationPool pool, RtpConfig config,
                              RtpMessages messages, RtpSounds sounds,
                              RtpCooldownStore cooldowns, RtpWarmupManager warmups) {
        this.plugin = plugin;
        this.pool = pool;
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
        this.cooldowns = cooldowns;
        this.warmups = warmups;
        this.transfers = new TeleportTransferStore(plugin, plugin.getRedisMessenger().getConnection().getPool());
    }

    public void overworld(Player player, String cluster) {
        begin(player, () -> pool.getRandomLocationInCluster(cluster, LocationDimension.OVERWORLD));
    }

    /**
     * The nether has no region selector, so the cluster is picked here: this server's own
     * cluster first, to keep the player in the region they are already playing in, then any
     * cluster that still has locations left.
     */
    public void nether(Player player) {
        begin(player, () -> currentCluster()
                .flatMap(cluster -> pool.getRandomLocationInCluster(cluster, LocationDimension.NETHER))
                .or(() -> pool.getRandomLocationAcrossClusters(
                        config.overworldClusters().keySet(), LocationDimension.NETHER)));
    }

    public void nether(Player player, String cluster) {
        begin(player, () -> pool.getRandomLocationInCluster(cluster, LocationDimension.NETHER));
    }

    public void end(Player player) {
        begin(player, () -> pool.getRandomLocation(config.endServer(), LocationDimension.END), true);
    }

    public void stop() {
        resolving.clear();
        cooldowns.clear();
    }

    public Optional<RtpLocation> randomOverworldLocation() {
        return pool.getRandomLocationGlobal(LocationDimension.OVERWORLD);
    }

    public boolean isBusy(UUID uuid) {
        return warmups.isActive(uuid) || resolving.contains(uuid);
    }

    public long remainingCooldown(UUID uuid) {
        return cooldowns.remainingSeconds(uuid);
    }

    public void startCooldown(UUID uuid) {
        cooldowns.start(uuid, config.cooldownSeconds());
    }

    public boolean teleportTo(UUID uuid, RtpLocation target) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && target.serverId().equalsIgnoreCase(plugin.getServerId())) {
            SchedulerUtil.execute(player, () -> teleportLocal(player, target));
            return true;
        }

        boolean stored = transfers.store(new TeleportTransferStore.CoordinateTarget(
                uuid, target.world(), target.x(), target.y(), target.z(), target.yaw(), target.pitch()));
        if (!stored) {
            return false;
        }
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(uuid.toString());
        event.setTargetServer(target.serverId());
        event.setWorld(target.world());
        event.setX(target.x());
        event.setY(target.y());
        event.setZ(target.z());
        event.setYaw(target.yaw());
        event.setPitch(target.pitch());
        plugin.getNetworkEventBus().publish(event);
        return true;
    }

    /** Reads Redis — only call from the async destination supplier. */
    private Optional<String> currentCluster() {
        if (plugin.getLocationPoolModule() == null || plugin.getLocationPoolModule().getLocationPool() == null) {
            return Optional.empty();
        }
        RedisLocationPool registry = plugin.getLocationPoolModule().getLocationPool();
        for (String cluster : config.overworldClusters().keySet()) {
            if (registry.getRegisteredServers(cluster, LocationDimension.OVERWORLD).contains(plugin.getServerId())) {
                return Optional.of(cluster);
            }
        }
        return Optional.empty();
    }

    private void begin(Player player, Supplier<Optional<RtpLocation>> destination) {
        begin(player, destination, false);
    }

    private void begin(Player player, Supplier<Optional<RtpLocation>> destination, boolean endRtp) {
        if (plugin.getCombatModule() != null && !plugin.getCombatModule().canTransfer(player, true)) return;
        UUID uuid = player.getUniqueId();
        if (warmups.isActive(uuid) || resolving.contains(uuid)) {
            messages.send(player, RtpMessage.ALREADY_TELEPORTING);
            return;
        }
        long remaining = cooldowns.remainingSeconds(uuid);
        if (remaining > 0) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    messages.get(RtpMessage.COOLDOWN_ACTIONBAR,
                            "%seconds%", String.valueOf(remaining))));
            sounds.play(player, "cooldown");
            return;
        }
        warmups.begin(player, () -> resolve(player, destination, endRtp));
    }

    private void resolve(Player player, Supplier<Optional<RtpLocation>> destination, boolean endRtp) {
        UUID uuid = player.getUniqueId();
        if (!resolving.add(uuid)) return;
        SchedulerUtil.runAsync(() -> {
            Optional<RtpLocation> selected = destination.get();
            SchedulerUtil.execute(player, () -> {
                resolving.remove(uuid);
                if (!player.isOnline()) return;
                if (selected.isEmpty()) {
                    messages.send(player, RtpMessage.NO_LOCATIONS);
                    sounds.play(player, "warmup-cancelled");
                    return;
                }
                teleport(player, selected.get(), endRtp);
            });
        });
    }

    private void teleport(Player player, RtpLocation target, boolean endRtp) {
        if (target.serverId().equalsIgnoreCase(plugin.getServerId())) {
            teleportLocal(player, target);
            return;
        }

        boolean stored = transfers.store(new TeleportTransferStore.CoordinateTarget(
                player.getUniqueId(), target.world(), target.x(), target.y(), target.z(), target.yaw(), target.pitch()));
        if (!stored) {
            messages.send(player, RtpMessage.TRANSFER_FAILED);
            sounds.play(player, "warmup-cancelled");
            return;
        }
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(target.serverId());
        if (endRtp) event.setPurpose("RTP_END");
        event.setWorld(target.world());
        event.setX(target.x());
        event.setY(target.y());
        event.setZ(target.z());
        event.setYaw(target.yaw());
        event.setPitch(target.pitch());
        plugin.getNetworkEventBus().publish(event);
        complete(player);
    }

    private void complete(Player player) {
        cooldowns.start(player.getUniqueId(), config.cooldownSeconds());
        sounds.play(player, "teleport-success");
    }

    private void teleportLocal(Player player, RtpLocation target) {
        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            messages.send(player, RtpMessage.WORLD_MISSING);
            sounds.play(player, "warmup-cancelled");
            return;
        }
        Location location = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        world.getChunkAtAsync(location.getBlockX() >> 4, location.getBlockZ() >> 4, true)
                .whenComplete((chunk, error) -> {
                    if (error != null || chunk == null) {
                        SchedulerUtil.execute(player, () -> {
                            messages.send(player, RtpMessage.TRANSFER_FAILED);
                            sounds.play(player, "warmup-cancelled");
                        });
                        return;
                    }
                    SchedulerUtil.execute(location, () -> {
                        if (!isSafeLocalLanding(location)) {
                            SchedulerUtil.execute(player, () -> {
                                messages.send(player, RtpMessage.NO_LOCATIONS);
                                sounds.play(player, "warmup-cancelled");
                            });
                            return;
                        }
                        player.teleportAsync(location).whenComplete((success, teleportError) ->
                                SchedulerUtil.execute(player, () -> {
                                    if (teleportError != null || !Boolean.TRUE.equals(success)
                                            || !player.isOnline()) {
                                        messages.send(player, RtpMessage.TRANSFER_FAILED);
                                        sounds.play(player, "warmup-cancelled");
                                        return;
                                    }
                                    complete(player);
                                }));
                    });
                });
    }

    private boolean isSafeLocalLanding(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Block ground = world.getBlockAt(x, y - 1, z);
        if (ground.getType().isAir() || unsafeLiquid(ground)) return false;
        if (world.getEnvironment() == World.Environment.NORMAL
                && world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY() != y - 1) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (unsafeLiquid(world.getBlockAt(x + dx, y, z + dz))
                        || unsafeLiquid(world.getBlockAt(x + dx, y + 1, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean unsafeLiquid(Block block) {
        return block.isLiquid()
                || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged());
    }
}
