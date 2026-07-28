package com.kammoun.raven.stats.listener;

import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.service.StatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StatsListener implements Listener {
    private final StatsService service;

    public StatsListener(StatsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        service.increment(event.getPlayer(), StatsType.BLOCKS_BROKEN);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        service.increment(event.getPlayer(), StatsType.BLOCKS_PLACED);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        service.increment(dead, StatsType.DEATHS);
        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) service.increment(killer, StatsType.KILLS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) service.increment(killer, StatsType.MOB_KILLS);
    }
}
