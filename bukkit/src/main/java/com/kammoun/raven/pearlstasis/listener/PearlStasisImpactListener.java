package com.kammoun.raven.pearlstasis.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.pearlstasis.PearlStasisRecord;
import com.kammoun.raven.library.redis.event.BorderCrossEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.pearlstasis.config.PearlStasisConfig;
import com.kammoun.raven.pearlstasis.db.PearlStasisRepository;
import com.kammoun.raven.pearlstasis.store.PearlStasisStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class PearlStasisImpactListener implements Listener {

    private final RavenCore plugin;
    private final PearlStasisConfig config;
    private final PearlStasisRepository repository;
    private final PearlStasisStore store;
    private final NetworkPlayerManager networkPlayerManager;
    private final NamespacedKey ownerKey;

    public PearlStasisImpactListener(RavenCore plugin, PearlStasisConfig config,
                                     PearlStasisRepository repository, PearlStasisStore store,
                                     NetworkPlayerManager networkPlayerManager) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.store = store;
        this.networkPlayerManager = networkPlayerManager;
        this.ownerKey = new NamespacedKey(plugin, "pearl_owner");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!config.isEnabled() || !(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        UUID ownerUuid = ownerUuid(pearl);
        if (ownerUuid != null) {
            pearl.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
            debug("Tracked pearl owner " + ownerUuid + " at launch " + format(pearl.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }

        UUID ownerUuid = ownerUuid(pearl);
        if (ownerUuid == null) {
            debug("Ignored pearl hit without owner at " + format(pearl.getLocation()));
            return;
        }

        Player localPlayer = Bukkit.getPlayer(ownerUuid);
        if (localPlayer != null && localPlayer.isOnline()) {
            // Owner is on the local server; normal vanilla & border listener handle it.
            debug("Ignored local pearl hit for " + localPlayer.getName() + " at " + format(pearl.getLocation()));
            return;
        }

        // Pearl landed on this server, but the owner is NOT on this server.
        event.setCancelled(true);
        pearl.remove();

        boolean onlineOnNetwork = networkPlayerManager.isOnNetwork(ownerUuid);
        if (!onlineOnNetwork) {
            debug("Owner " + ownerUuid + " was not visible in network registry at pearl hit; "
                    + "recording pending stasis anyway.");
        }

        Location impactLoc = pearl.getLocation();
        if (event.getHitBlock() != null) {
            impactLoc = event.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
            impactLoc.setYaw(pearl.getLocation().getYaw());
            impactLoc.setPitch(pearl.getLocation().getPitch());
        } else if (event.getHitEntity() != null) {
            impactLoc = event.getHitEntity().getLocation();
        }

        String targetServer = plugin.getServerId();
        if (plugin.getBorderModule() != null && impactLoc.getWorld() != null) {
            GridCell cell = plugin.getBorderModule().getBorderConfig().cellFor(impactLoc.getWorld().getEnvironment());
            if (cell != null) {
                GridCell.Direction dir = cell.exitDirection(impactLoc.getX(), impactLoc.getZ());
                if (dir != null) {
                    int neighborCellX = plugin.getBorderModule().getBorderConfig().cellX() + dir.dx;
                    int neighborCellZ = plugin.getBorderModule().getBorderConfig().cellZ() + dir.dz;
                    String resolved = plugin.getBorderModule().getRegistry().resolve(
                            plugin.getBorderModule().getBorderConfig().cluster(),
                            impactLoc.getWorld().getEnvironment().name(),
                            neighborCellX, neighborCellZ
                    );
                    if (resolved != null && !resolved.isBlank()) {
                        targetServer = resolved;
                    }
                }
            }
        }

        final String finalTargetServer = targetServer;
        final Location finalImpactLoc = impactLoc;
        final UUID finalOwnerUuid = ownerUuid;

        SchedulerUtil.runAsync(() -> {
            try {
                PearlStasisRecord record = new PearlStasisRecord(
                        finalOwnerUuid,
                        finalTargetServer,
                        finalImpactLoc.getWorld().getName(),
                        finalImpactLoc.getX(),
                        finalImpactLoc.getY(),
                        finalImpactLoc.getZ(),
                        finalImpactLoc.getYaw(),
                        finalImpactLoc.getPitch(),
                        System.currentTimeMillis()
                );

                repository.savePending(record);
                store.setMarker(finalOwnerUuid, config.isExpirePendingRecords() ? config.getTtlSeconds() : 0);

                BorderCrossEvent transferEvent = new BorderCrossEvent();
                transferEvent.setPlayerUuid(finalOwnerUuid.toString());
                transferEvent.setSourceServer(plugin.getServerId());
                transferEvent.setTargetServer(finalTargetServer);
                transferEvent.setDimension(finalImpactLoc.getWorld().getEnvironment().name());
                transferEvent.setX(finalImpactLoc.getX());
                transferEvent.setY(finalImpactLoc.getY());
                transferEvent.setZ(finalImpactLoc.getZ());
                transferEvent.setYaw(finalImpactLoc.getYaw());
                transferEvent.setPitch(finalImpactLoc.getPitch());
                if (plugin.getCombatModule() != null) {
                    plugin.getCombatModule().markNetworkTransfer(finalOwnerUuid);
                }
                plugin.getNetworkEventBus().publish(transferEvent);
                if (config.isLogTransfers()) {
                    plugin.getLogger().info("[PearlStasis] Recorded landing for " + finalOwnerUuid
                            + " -> " + finalTargetServer + " at " + format(finalImpactLoc) + ".");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[PearlStasis] Failed to record stasis landing for "
                        + finalOwnerUuid + ": " + e.getMessage());
            }
        });
    }

    private UUID ownerUuid(EnderPearl pearl) {
        UUID ownerUuid = pearl.getOwnerUniqueId();
        if (ownerUuid == null) {
            String stored = pearl.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (stored != null && !stored.isBlank()) {
                try {
                    ownerUuid = UUID.fromString(stored);
                } catch (IllegalArgumentException ignored) {
                    debug("Ignored invalid stored pearl owner '" + stored + "' at " + format(pearl.getLocation()));
                }
            }
        }
        if (ownerUuid == null && pearl.getShooter() instanceof Player player) {
            ownerUuid = player.getUniqueId();
        }
        if (ownerUuid == null && pearl.getShooter() instanceof OfflinePlayer offlinePlayer) {
            ownerUuid = offlinePlayer.getUniqueId();
        }
        return ownerUuid;
    }

    private void debug(String message) {
        if (config.isDebug()) {
            plugin.getLogger().info("[PearlStasis] " + message);
        }
    }

    private String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " "
                + String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f",
                location.getX(), location.getY(), location.getZ());
    }
}
