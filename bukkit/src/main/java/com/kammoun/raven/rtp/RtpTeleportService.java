package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.rtp.config.RtpConfig;
import com.kammoun.raven.rtp.config.RtpMessage;
import com.kammoun.raven.rtp.config.RtpMessages;
import com.kammoun.raven.tpa.transfer.TeleportTransferStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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

    public void nether(Player player) {
        begin(player, () -> pool.getRandomLocationAcrossClusters(
                config.overworldClusters().keySet(), LocationDimension.NETHER));
    }

    public void end(Player player) {
        begin(player, () -> pool.getRandomLocation(config.endServer(), LocationDimension.END));
    }

    public void stop() {
        resolving.clear();
        cooldowns.clear();
    }

    private void begin(Player player, Supplier<Optional<RtpLocation>> destination) {
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
        warmups.begin(player, () -> resolve(player, destination));
    }

    private void resolve(Player player, Supplier<Optional<RtpLocation>> destination) {
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
                teleport(player, selected.get());
            });
        });
    }

    private void teleport(Player player, RtpLocation target) {
        if (target.serverId().equalsIgnoreCase(plugin.getServerId())) {
            World world = Bukkit.getWorld(target.world());
            if (world == null) {
                messages.send(player, RtpMessage.WORLD_MISSING);
                sounds.play(player, "warmup-cancelled");
                return;
            }
            Location location = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
            player.teleportAsync(location).thenAccept(success -> SchedulerUtil.execute(player, () -> {
                if (!success || !player.isOnline()) {
                    messages.send(player, RtpMessage.TRANSFER_FAILED);
                    sounds.play(player, "warmup-cancelled");
                    return;
                }
                complete(player);
            }));
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
}
