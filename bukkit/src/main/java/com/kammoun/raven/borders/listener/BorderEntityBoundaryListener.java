package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class BorderEntityBoundaryListener implements Listener {

    private final BorderModule module;

    public BorderEntityBoundaryListener(BorderModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getTo() == null || !outside(event.getTo())) {
            return;
        }

        event.setCancelled(true);
        deny(event.getPlayer());
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
            watchProjectile(projectile);
        }
    }

    private void watchProjectile(Projectile projectile) {
        projectile.getScheduler().runDelayed(module.getApi(), task -> {
            if (!projectile.isValid()) {
                return;
            }

            if (outside(projectile.getLocation())) {
                Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : null;
                projectile.remove();
                if (projectile instanceof EnderPearl && shooter instanceof Player player) {
                    deny(player);
                }
                return;
            }

            watchProjectile(projectile);
        }, null, 1L);
    }

    private boolean outside(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        GridCell cell = module.getBorderConfig().cellFor(location.getWorld().getEnvironment());
        return cell != null && !cell.contains(location.getX(), location.getZ());
    }

    private void deny(Player player) {
        String message = module.getBorderConfig().denyMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(ChatFormatter.color(message));
        }
    }
}
