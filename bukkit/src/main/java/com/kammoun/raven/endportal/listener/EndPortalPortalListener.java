package com.kammoun.raven.endportal.listener;

import com.kammoun.raven.endportal.EndPortalService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Vanilla path, used whenever the server still owns the dimension the portal points
 * at. Runs below the border listener's HIGH so a cancelled crossing never reaches
 * the grid check, which has no cell for THE_END anyway.
 */
public class EndPortalPortalListener implements Listener {

    private final EndPortalService service;

    public EndPortalPortalListener(EndPortalService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!service.claim(player)) return;

        if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
            service.sendToOverworld(player);
        } else {
            service.sendToEnd(player);
        }
    }
}
