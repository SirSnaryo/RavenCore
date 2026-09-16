package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.utils.BorderTransferStore;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/*apply any pending border arrival stored by the source server*/
public class PlayerListener implements Listener {


    private final BorderModule plugin;

    public PlayerListener(BorderModule plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (event.getConnection().getProfile().getId() == null) {
            return;
        }
        BorderTransferStore.Arrival arrival = plugin.getTransferStore()
                .consume(event.getConnection().getProfile().getId(), plugin.getApi().getServerId());
        if (arrival == null) return;

        Location location = locationFor(arrival);
        if (location != null) {
            event.setSpawnLocation(location);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) return;
            BorderTransferStore.Arrival arrival = plugin.getTransferStore()
                    .consume(player.getUniqueId(), plugin.getApi().getServerId());
            if (arrival == null) return;

            Location loc = locationFor(arrival);
            if (loc != null) {
                player.setFallDistance(0);
                player.teleportAsync(loc);
            }
        }), 1L);
    }

    private Location locationFor(BorderTransferStore.Arrival arrival) {
        World.Environment env;
        try {
            env = World.Environment.valueOf(arrival.dimension());
        } catch (IllegalArgumentException ex) {
            return null;
        }

        for (World world : plugin.getApi().getServer().getWorlds()) {
            if (world.getEnvironment() == env) {
                GridCell cell = plugin.getBorderConfig().cellFor(env);
                double inset = plugin.getBorderConfig().arrivalInsetBlocks();
                double x = cell == null ? arrival.x() : clamp(arrival.x(), cell.minX() + inset, cell.maxX() - inset);
                double z = cell == null ? arrival.z() : clamp(arrival.z(), cell.minZ() + inset, cell.maxZ() - inset);
                return new Location(world, x, arrival.y(), z, arrival.yaw(), arrival.pitch());
            }
        }
        return null;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
