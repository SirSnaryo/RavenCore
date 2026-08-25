package com.kammoun.raven.joinfix.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinFixListener implements Listener {
    private final RavenCore plugin;
    private final long delayTicks;

    public JoinFixListener(RavenCore plugin, long delayTicks) {
        this.plugin = plugin;
        this.delayTicks = Math.max(1L, delayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> clearStuckState(player)), delayTicks);
    }

    private void clearStuckState(Player player) {
        if (!player.isOnline()) return;

        boolean wasStuck = player.isInvulnerable()
                || player.getNoDamageTicks() > player.getMaximumNoDamageTicks()
                || !player.isCollidable();

        player.setInvulnerable(false);
        player.setNoDamageTicks(0);
        player.setMaximumNoDamageTicks(20);
        player.setCollidable(true);

        if (wasStuck) {
            plugin.getLogger().warning("[JoinFix] Cleared a stuck invulnerable/no-collision state for "
                    + player.getName() + " on join.");
        }
    }
}
