package com.kammoun.raven.borders.listener;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.utils.BorderTransferStore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/*apply any pending border arrival stored by the source server*/
public class PlayerListener implements Listener {


    private final BorderModule plugin;

    public PlayerListener(BorderModule plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        SchedulerUtil.execute(player, () -> {
            BorderTransferStore.Arrival arrival = plugin.getTransferStore().consume(player.getUniqueId());
            if (arrival == null) return;

            World.Environment env;
            try {
                env = World.Environment.valueOf(arrival.dimension());
            } catch (IllegalArgumentException ex) {
                return;
            }

            // Find the matching world on this server
            for (World world : plugin.getApi().getServer().getWorlds()) {
                if (world.getEnvironment() == env) {
                    Location loc = new org.bukkit.Location(world, arrival.x(), arrival.y(), arrival.z(),
                            arrival.yaw(), arrival.pitch());
                    player.teleportAsync(loc);
                    return;
                }
            }
        });
    }

}
