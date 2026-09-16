package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.utils.GridCell;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public final class BorderEntityBoundaryListener implements Listener {

    private static final int PROJECTILE_CHECK_INTERVAL_TICKS = 5;
    private static final int PROJECTILE_MAX_CHECKS = 80;

    private final BorderModule module;

    public BorderEntityBoundaryListener(BorderModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getTo() == null || !outside(event.getTo())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Player || !outside(event.getLocation())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Projectile projectile) {
            watchProjectile(projectile, 0);
        }
    }

    private void watchProjectile(Projectile projectile, int checks) {
        if (projectile instanceof EnderPearl) {
            return;
        }
        if (checks >= PROJECTILE_MAX_CHECKS) {
            return;
        }

        projectile.getScheduler().runDelayed(module.getApi(), task -> {
            if (!projectile.isValid()) {
                return;
            }

            if (outside(projectile.getLocation())) {
                projectile.remove();
                return;
            }

            watchProjectile(projectile, checks + 1);
        }, null, PROJECTILE_CHECK_INTERVAL_TICKS);
    }

    private boolean outside(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        GridCell cell = module.getBorderConfig().cellFor(location.getWorld().getEnvironment());
        return cell != null && !cell.contains(location.getX(), location.getZ());
    }

}
