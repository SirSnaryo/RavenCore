package com.kammoun.raven.endportal.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.endportal.EndPortalService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Leaving the End is a respawn, not a portal teleport — vanilla runs the exit
 * through the win-screen path, so this is the only event that always fires for it.
 * Runs before {@code RespawnManager} (HIGHEST), which would otherwise treat the
 * exit as a death and resolve it to the player's bed.
 */
public class EndPortalRespawnListener implements Listener {

    private final RavenCore plugin;
    private final EndPortalService service;

    public EndPortalRespawnListener(RavenCore plugin, EndPortalService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isEndPortalExit(event)) return;

        Player player = event.getPlayer();
        // Park them in the End until the destination resolves; the vanilla respawn
        // location is this server's void overworld, which is not somewhere to land.
        World end = endWorld();
        if (end != null) {
            event.setRespawnLocation(end.getSpawnLocation());
        }

        if (!service.claim(player)) return;
        service.sendToOverworld(player);
    }

    public static boolean isEndPortalExit(PlayerRespawnEvent event) {
        return event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.END_PORTAL;
    }

    private World endWorld() {
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.THE_END)
                .findFirst()
                .orElse(null);
    }
}
