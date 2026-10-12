package com.kammoun.raven.spawn.listener;

import com.kammoun.raven.spawn.afk.AfkAnchorService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class AfkAnchorListener implements Listener {

    private final AfkAnchorService service;

    public AfkAnchorListener(AfkAnchorService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (blocked(event.getFrom(), event.getTo())) return;
        service.hold(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (blocked(event.getFrom(), event.getTo())) return;
        service.hold(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getLocation().getY() > player.getWorld().getMinHeight()) return;
        if (!service.hold(player)) return;
        event.setCancelled(true);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.release(event.getPlayer());
    }

    private boolean blocked(Location from, Location to) {
        if (to == null) return true;
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
