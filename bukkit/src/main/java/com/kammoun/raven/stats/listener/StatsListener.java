package com.kammoun.raven.stats.listener;

import com.kammoun.raven.stats.model.StatsType;
import com.kammoun.raven.stats.service.StatsService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int crafted = Math.max(1, event.getCurrentItem() == null ? 1 : event.getCurrentItem().getAmount());
        service.increment(player.getUniqueId(), StatsType.ITEMS_CRAFTED, crafted);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatistic(PlayerStatisticIncrementEvent event) {
        if (event.getStatistic() == Statistic.JUMP) {
            service.increment(event.getPlayer(), StatsType.JUMPS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        service.increment(event.getPlayer(), StatsType.CHAT_MESSAGES);
    }
}
