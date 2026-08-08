package com.kammoun.raven.endportal.listener;

import com.kammoun.raven.endportal.EndPortalService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Catch-all for the End server: leaving the End locally can only mean an exit that
 * slipped past the respawn and portal-block paths, and the only non-End world here is
 * the placeholder overworld the server needs to boot. Rather than leave the player
 * standing in it, finish the crossing they started.
 */
public class EndPortalEscapeListener implements Listener {

    private final EndPortalService service;

    public EndPortalEscapeListener(EndPortalService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (event.getFrom().getEnvironment() != World.Environment.THE_END) return;

        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() == World.Environment.THE_END) return;
        if (!service.claim(player)) return;

        service.sendToOverworld(player);
    }
}
