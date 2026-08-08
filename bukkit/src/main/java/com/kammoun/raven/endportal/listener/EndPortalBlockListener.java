package com.kammoun.raven.endportal.listener;

import com.kammoun.raven.endportal.EndPortalService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * CraftBukkit bails out of end-portal handling before firing any event when the
 * destination dimension is missing, so a survival server running with
 * {@code allow-end: false} never sees {@link org.bukkit.event.player.PlayerPortalEvent}.
 * Watching for the portal block itself keeps the crossing working with the local
 * End world deleted.
 */
public class EndPortalBlockListener implements Listener {

    private final EndPortalService service;

    public EndPortalBlockListener(EndPortalService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (to.getBlock().getType() != Material.END_PORTAL) return;

        Player player = event.getPlayer();
        if (!service.claim(player)) return;

        if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
            service.sendToOverworld(player);
        } else {
            service.sendToEnd(player);
        }
    }
}
