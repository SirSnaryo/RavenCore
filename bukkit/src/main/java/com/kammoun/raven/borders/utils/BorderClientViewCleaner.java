package com.kammoun.raven.borders.utils;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Clears old server-side entities from a client before a world-preserving
 * Continuum switch. The destination server will spawn its real entities after
 * the connection moves.
 */
public final class BorderClientViewCleaner {

    private final RavenCore plugin;
    private final BorderConfig config;

    public BorderClientViewCleaner(RavenCore plugin, BorderConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void clearBeforeTransfer(Player player) {
        if (!config.cleanupClientEntitiesOnTransfer()) {
            return;
        }

        int maxEntities = config.cleanupClientEntitiesMax();
        if (maxEntities <= 0) {
            return;
        }

        double radius = Math.min(config.cleanupClientEntitiesRadius(), 64.0);
        Collection<Entity> nearby = player.getNearbyEntities(radius, radius, radius);
        if (nearby.isEmpty()) {
            return;
        }

        List<Entity> hidden = new ArrayList<>(Math.min(nearby.size(), maxEntities));
        for (Entity entity : nearby) {
            if (entity == null || entity.equals(player) || !entity.isValid()) {
                continue;
            }
            player.hideEntity(plugin, entity);
            hidden.add(entity);
            if (hidden.size() >= maxEntities) {
                break;
            }
        }

        if (!hidden.isEmpty()) {
            SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> restoreIfStillHere(player, hidden)), 100L);
        }
    }

    private void restoreIfStillHere(Player player, List<Entity> hidden) {
        if (!player.isOnline()) {
            return;
        }
        for (Entity entity : hidden) {
            if (entity != null && entity.isValid()) {
                player.showEntity(plugin, entity);
            }
        }
    }
}
