package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.registery.BorderRegistry;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.utils.BorderClientViewCleaner;
import com.kammoun.raven.borders.utils.BorderTransferStore;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.library.redis.event.BorderCrossEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BorderMovementListener implements Listener {

    private final BorderConfig config;
    private final BorderRegistry registry;
    private final BorderTransferStore transferStore;
    private final NetworkEventBus eventBus;
    private final String sourceServer;
    private final RavenCore plugin;
    private final BorderClientViewCleaner clientViewCleaner;

    /** Players currently being transferred — suppress duplicate triggers. */
    private final Set<UUID> inTransfer = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingVehicleTransfer = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> suppressedUntil = new ConcurrentHashMap<>();

    public BorderMovementListener(RavenCore plugin, BorderConfig config, BorderRegistry registry,
                                  BorderTransferStore transferStore, NetworkEventBus eventBus) {
        this.config = config;
        this.plugin = plugin;
        this.registry = registry;
        this.transferStore = transferStore;
        this.eventBus = eventBus;
        this.sourceServer = plugin.getConfig().getString("network.server-id", "").trim();
        this.clientViewCleaner = new BorderClientViewCleaner(plugin, config);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only act when the player actually moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        checkAndTransfer(event.getPlayer(), event.getTo().getX(), event.getTo().getY(),
                event.getTo().getZ(), event.getTo().getYaw(), event.getTo().getPitch(),
                event.getTo().getWorld().getEnvironment(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player) {
                handleVehicleBorderMove(player, event);
                return;
            }
        }
    }

    private void handleVehicleBorderMove(Player player, VehicleMoveEvent event) {
        GridCell cell = config.cellFor(event.getTo().getWorld().getEnvironment());
        if (cell == null || cell.contains(event.getTo().getX(), event.getTo().getZ())) {
            return;
        }
        if (!pendingVehicleTransfer.add(player.getUniqueId())) {
            return;
        }

        double x = event.getTo().getX();
        double y = event.getTo().getY();
        double z = event.getTo().getZ();
        float yaw = event.getTo().getYaw();
        float pitch = event.getTo().getPitch();
        World.Environment environment = event.getTo().getWorld().getEnvironment();

        event.getVehicle().eject();
        player.leaveVehicle();

        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            pendingVehicleTransfer.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
                returnInsideCurrentCell(player);
                deny(player);
                return;
            }
            checkAndTransfer(player, x, y, z, yaw, pitch, environment, null);
        }), 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        suppress(player.getUniqueId(), 5_000L);
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) return;
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) return;
            checkAndTransfer(player, location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), world.getEnvironment(), null);
        }), 101L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        suppressedUntil.remove(uuid);
        inTransfer.remove(uuid);
        pendingVehicleTransfer.remove(uuid);
    }

    public void suppress(UUID playerUuid, long durationMillis) {
        suppressedUntil.put(playerUuid, System.currentTimeMillis() + durationMillis);
    }

    public void release(UUID playerUuid) {
        suppressedUntil.remove(playerUuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Skip portal events — handled separately below
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

        checkAndTransfer(event.getPlayer(), event.getTo().getX(), event.getTo().getY(),
                event.getTo().getZ(), event.getTo().getYaw(), event.getTo().getPitch(),
                event.getTo().getWorld().getEnvironment(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        // Null when the destination dimension is unavailable, e.g. an End portal on a
        // server running without its own End world.
        if (event.getTo() == null || event.getTo().getWorld() == null) return;

        checkAndTransfer(event.getPlayer(), event.getTo().getX(), event.getTo().getY(),
                event.getTo().getZ(), event.getTo().getYaw(), event.getTo().getPitch(),
                event.getTo().getWorld().getEnvironment(), event);
    }

    // -------------------------------------------------------------------------

    private void checkAndTransfer(Player player, double x, double y, double z,
                                  float yaw, float pitch, World.Environment env,
                                  org.bukkit.event.Cancellable event) {
        Long suppressionExpiry = suppressedUntil.get(player.getUniqueId());
        if (suppressionExpiry != null) {
            if (suppressionExpiry > System.currentTimeMillis()) return;
            suppressedUntil.remove(player.getUniqueId(), suppressionExpiry);
        }
        if (inTransfer.contains(player.getUniqueId())) return;
        if (pendingVehicleTransfer.contains(player.getUniqueId())) return;

        GridCell cell = config.cellFor(env);
        if (cell == null) return; // dimension not managed on this server

        GridCell.Direction dir = cell.exitDirection(x, z);
        if (dir == null) return; // still inside

        int neighborCellX = config.cellX() + dir.dx;
        int neighborCellZ = config.cellZ() + dir.dz;
        String dimension = env.name();

        String targetServer = registry.resolve(config.cluster(), dimension, neighborCellX, neighborCellZ);
        if (targetServer == null) {
            if (event != null) event.setCancelled(true);
            returnInsideCurrentCell(player);
            deny(player);
            return;
        }
        if (targetServer.equalsIgnoreCase(sourceServer)) {
            if (event != null) event.setCancelled(true);
            returnInsideCurrentCell(player);
            return;
        }

        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            if (player.isInsideVehicle()) {
                return;
            }
        }

        inTransfer.add(player.getUniqueId());
        suppress(player.getUniqueId(), 3_000L);
        if (plugin.getCombatModule() != null) {
            plugin.getCombatModule().markNetworkTransfer(player.getUniqueId());
        }

        clientViewCleaner.clearBeforeTransfer(player);
        transferStore.store(player.getUniqueId(), targetServer, dimension, x, y, z, yaw, pitch);

        BorderCrossEvent crossEvent = new BorderCrossEvent();
        crossEvent.setPlayerUuid(player.getUniqueId().toString());
        crossEvent.setSourceServer(sourceServer);
        crossEvent.setTargetServer(targetServer);
        crossEvent.setDimension(dimension);
        crossEvent.setX(x);
        crossEvent.setY(y);
        crossEvent.setZ(z);
        crossEvent.setYaw(yaw);
        crossEvent.setPitch(pitch);
        eventBus.publish(crossEvent);

        // Remove transfer lock after a short delay so re-entry is possible if transfer fails
        SchedulerUtil.runLater(() -> inTransfer.remove(player.getUniqueId()), 60L);
    }

    public void onTransferFailed(UUID playerUuid) {
        inTransfer.remove(playerUuid);
        suppressedUntil.remove(playerUuid);
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null) {
            return;
        }
        returnInsideCurrentCell(player);
        deny(player);
    }

    public void returnInsideCurrentCell(Player player) {
        SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) return;
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) return;
            GridCell cell = config.cellFor(world.getEnvironment());
            if (cell == null || cell.contains(location.getX(), location.getZ())) return;

            double x = clamp(location.getX(), cell.minX() + 0.5, cell.maxX() - 0.5);
            double z = clamp(location.getZ(), cell.minZ() + 0.5, cell.maxZ() - 0.5);
            Location safe = new Location(world, x, location.getY(), z, location.getYaw(), location.getPitch());
            suppress(player.getUniqueId(), 2_000L);
            player.teleportAsync(safe);
        });
    }

    private void deny(Player player) {
        String message = config.transferFailureMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(ChatFormatter.color(message));
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
