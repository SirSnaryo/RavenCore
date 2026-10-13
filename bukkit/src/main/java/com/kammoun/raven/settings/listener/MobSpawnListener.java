package com.kammoun.raven.settings.listener;

import com.kammoun.raven.settings.service.MobSpawnSettingsService;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Cancels hostile spawns inside the radius of a player who switched mob spawning off. Only that
 * player is affected: everyone else in the same area still gets normal spawns.
 */
public final class MobSpawnListener implements Listener {

    private final MobSpawnSettingsService service;
    private final double radiusSquared;

    public MobSpawnListener(MobSpawnSettingsService service, int radius) {
        this.service = service;
        this.radiusSquared = Math.max(1, radius) * (double) Math.max(1, radius);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        if (!service.anyDisabled()) return;

        Location spawn = event.getLocation();
        for (Player player : spawn.getWorld().getPlayers()) {
            if (!service.allowsMobSpawn(player.getUniqueId())) continue;
            if (player.getLocation().distanceSquared(spawn) <= radiusSquared) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
