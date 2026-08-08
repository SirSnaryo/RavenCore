package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.registery.BorderRegistry;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.utils.BorderTransferStore;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.library.redis.event.BorderCrossEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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

    /** Players currently being transferred — suppress duplicate triggers. */
    private final Set<UUID> inTransfer = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> suppressedUntil = new ConcurrentHashMap<>();

    public BorderMovementListener(RavenCore plugin, BorderConfig config, BorderRegistry registry,
                                  BorderTransferStore transferStore, NetworkEventBus eventBus) {
        this.config = config;
        this.plugin = plugin;
        this.registry = registry;
        this.transferStore = transferStore;
        this.eventBus = eventBus;
        this.sourceServer = plugin.getConfig().getString("network.server-id", "").trim();
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        suppress(event.getPlayer().getUniqueId(), 5_000L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        suppressedUntil.remove(uuid);
        inTransfer.remove(uuid);
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

        GridCell cell = config.cellFor(env);
        if (cell == null) return; // dimension not managed on this server

        GridCell.Direction dir = cell.exitDirection(x, z);
        if (dir == null) return; // still inside

        if (plugin.getCombatModule() != null && !plugin.getCombatModule().canTransfer(player, true)) {
            event.setCancelled(true);
            return;
        }

        int neighborCellX = config.cellX() + dir.dx;
        int neighborCellZ = config.cellZ() + dir.dz;
        String dimension = env.name();

        String targetServer = registry.resolve(config.cluster(), dimension, neighborCellX, neighborCellZ);
        if (targetServer == null) {
            event.setCancelled(true);
            kickForTransferFailure(player);
            return;
        }
        if (targetServer.equalsIgnoreCase(sourceServer)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            if (player.isInsideVehicle()) {
                return;
            }
        }

        inTransfer.add(player.getUniqueId());

        transferStore.store(player.getUniqueId(), dimension, x, y, z, yaw, pitch);

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

    private void kickForTransferFailure(Player player) {
        SchedulerUtil.execute(player, () ->
                player.kickPlayer(ChatFormatter.color(config.transferFailureMessage())));
    }
}
