package com.kammoun.raven.home.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.home.config.HomeConfig;
import com.kammoun.raven.home.config.HomeMessages;
import com.kammoun.raven.home.model.HomeRecord;
import com.kammoun.raven.home.api.HomeDestination;
import com.kammoun.raven.home.api.HomeTeleportGateway;
import com.kammoun.raven.home.db.HomeRepository;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.tpa.transfer.TeleportTransferStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class HomeService implements HomeTeleportGateway {
    private final RavenCore plugin;
    private final HomeRepository repository;
    private final HomeConfig config;
    private final HomeMessages messages;
    private final HomeWarmup warmup;
    private final TeleportTransferStore transfers;

    public HomeService(RavenCore plugin, HomeRepository repository, HomeConfig config,
                       HomeMessages messages, HomeWarmup warmup) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.messages = messages;
        this.warmup = warmup;
        this.transfers = new TeleportTransferStore(plugin,
                plugin.getRedisMessenger().getConnection().getPool());
    }

    public void set(Player player, String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            messages.send(player, "invalid-name");
            return;
        }
        Location snapshot = player.getLocation().clone();
        UUID uuid = player.getUniqueId();
        int homeLimit = limit(player);
        SchedulerUtil.runAsync(() -> {
            try {
                boolean exists = repository.find(uuid, name).isPresent();
                if (!exists && repository.count(uuid) >= homeLimit) {
                    SchedulerUtil.execute(player, () -> messages.send(player, "limit-reached",
                            "%limit%", String.valueOf(homeLimit)));
                    return;
                }
                repository.upsert(HomeRecord.from(uuid, name, plugin.getServerId(), snapshot));
                SchedulerUtil.execute(player, () -> messages.send(player, "set", "%home%", name));
            } catch (SQLException | RuntimeException e) {
                fail(player, e);
            }
        });
    }

    public void delete(Player player, String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            messages.send(player, "invalid-name");
            return;
        }
        SchedulerUtil.runAsync(() -> {
            try {
                boolean deleted = repository.delete(player.getUniqueId(), name);
                SchedulerUtil.execute(player, () -> messages.send(player, deleted ? "deleted" : "not-found",
                        "%home%", name));
            } catch (SQLException | RuntimeException e) {
                fail(player, e);
            }
        });
    }

    public void teleport(Player player, String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            messages.send(player, "invalid-name");
            return;
        }
        SchedulerUtil.runAsync(() -> {
            try {
                Optional<HomeRecord> result = repository.find(player.getUniqueId(), name);
                SchedulerUtil.execute(player, () -> {
                    if (!player.isOnline()) return;
                    if (result.isEmpty()) {
                        messages.send(player, "not-found", "%home%", name);
                        return;
                    }
                    HomeRecord home = result.get();
                    teleport(player, new HomeDestination(home.name(), home.serverId(), home.world(),
                            home.x(), home.y(), home.z(), home.yaw(), home.pitch()));
                });
            } catch (SQLException | RuntimeException e) {
                fail(player, e);
            }
        });
    }

    public void list(Player player, Consumer<List<HomeRecord>> callback) {
        SchedulerUtil.runAsync(() -> {
            try {
                List<HomeRecord> homes = repository.findAll(player.getUniqueId());
                SchedulerUtil.execute(player, () -> {
                    if (player.isOnline()) callback.accept(homes);
                });
            } catch (SQLException | RuntimeException e) {
                fail(player, e);
            }
        });
    }

    @Override
    public void teleport(Player player, HomeDestination destination) {
        if (!destination.serverId().equalsIgnoreCase(plugin.getServerId())
                && plugin.getCombatModule() != null
                && !plugin.getCombatModule().canTransfer(player, true)) return;
        warmup.begin(player, destination.displayName(), () -> send(player, destination));
    }

    private void send(Player player, HomeDestination destination) {
        if (destination.serverId().equalsIgnoreCase(plugin.getServerId())) {
            World world = Bukkit.getWorld(destination.world());
            if (world == null) {
                messages.send(player, "world-missing", "%world%", destination.world());
                return;
            }
            player.teleportAsync(new Location(world, destination.x(), destination.y(), destination.z(),
                            destination.yaw(), destination.pitch()))
                    .thenAccept(success -> SchedulerUtil.execute(player, () -> messages.send(player,
                            success ? "teleported" : "transfer-failed", "%home%", destination.displayName())));
            return;
        }
        boolean stored = transfers.store(new TeleportTransferStore.CoordinateTarget(player.getUniqueId(),
                destination.world(), destination.x(), destination.y(), destination.z(),
                destination.yaw(), destination.pitch()));
        if (!stored) {
            messages.send(player, "transfer-failed", "%home%", destination.displayName());
            return;
        }
        CrossServerTeleportEvent event = new CrossServerTeleportEvent();
        event.setPlayerUuid(player.getUniqueId().toString());
        event.setTargetServer(destination.serverId());
        event.setWorld(destination.world());
        event.setX(destination.x());
        event.setY(destination.y());
        event.setZ(destination.z());
        event.setYaw(destination.yaw());
        event.setPitch(destination.pitch());
        plugin.getNetworkEventBus().publish(event);
        messages.send(player, "connecting", "%home%", destination.displayName(),
                "%server%", destination.serverId());
    }

    private int limit(Player player) {
        int limit = config.defaultLimit();
        for (int i = 1; i <= 20; i++) if (player.hasPermission("raven.home.max." + i)) limit = Math.max(limit, i);
        return limit;
    }

    public int limitFor(Player player) {
        return limit(player);
    }

    private String normalize(String raw) {
        if (raw == null) return null;
        String name = raw.trim().toLowerCase(Locale.ROOT);
        return name.matches("[a-z0-9_-]{1,32}") ? name : null;
    }

    private void fail(Player player, Exception e) {
        plugin.getLogger().warning("[Home] Database operation failed for " + player.getName() + ": " + e.getMessage());
        SchedulerUtil.execute(player, () -> {
            if (player.isOnline()) messages.send(player, "database-error");
        });
    }
}
