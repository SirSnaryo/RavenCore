package com.kammoun.raven.survival.listener;

import com.kammoun.raven.survival.SurvivalModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class SurvivalListener implements Listener {

    private final SurvivalModule module;

    public SurvivalListener(SurvivalModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFoodLevel() >= module.getFoodLevel()) return;
        if (!applies(player)) return;
        event.setCancelled(true);
        apply(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!applies(player)) return;
        apply(player);
    }

    private boolean applies(Player player) {
        return module.isHungerDisabled() && !module.isWorldIgnored(player.getWorld().getName());
    }

    private void apply(Player player) {
        player.setFoodLevel(module.getFoodLevel());
        player.setSaturation(module.getSaturation());
    }
}
