package com.kammoun.raven.combat.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.combat.config.CombatConfig;
import com.kammoun.raven.combat.service.CombatService;
import com.kammoun.raven.combat.service.ProtectedRegionChecker;
import com.kammoun.raven.library.redis.event.ChatBroadcastEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class CombatListener implements Listener {
    private final RavenCore plugin;
    private final CombatService combat;
    private final CombatConfig config;
    private final ProtectedRegionChecker regions;

    public CombatListener(RavenCore plugin, CombatService combat, CombatConfig config,
                          ProtectedRegionChecker regions) {
        this.plugin = plugin;
        this.combat = combat;
        this.config = config;
        this.regions = regions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event.getDamager());
        if (attacker != null && !attacker.equals(victim)) combat.tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID victimId = event.getPlayer().getUniqueId();
        UUID opponentId = combat.opponent(victimId).orElse(null);
        combat.remove(victimId, false, true);

        Player killer = event.getPlayer().getKiller();
        UUID otherId = killer == null ? opponentId : killer.getUniqueId();
        if (otherId != null && !otherId.equals(victimId)) {
            combat.remove(otherId, true, true);
        }

        if (killer != null) {
            stealMoney(killer, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combat.isTagged(player) || plugin.getCombatModule().isServerStopping()) return;
        combat.remove(player.getUniqueId(), false, true);
        if (!config.killOnLogout()) return;
        player.setHealth(0.0);
        ChatBroadcastEvent broadcast = new ChatBroadcastEvent();
        broadcast.setMessage(config.message("logout-death", "%player%", player.getName()));
        plugin.getNetworkEventBus().publish(broadcast);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        combat.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!combat.isTagged(event.getPlayer()) || !config.blocked(event.getMessage())) return;
        event.setCancelled(true);
        String base = event.getMessage().substring(1).split(" ", 2)[0];
        config.send(event.getPlayer(), "blocked-command", "%command%", base);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player
                && config.disableElytra() && combat.isTagged(player)) {
            event.setCancelled(true);
            config.send(player, "elytra-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.ENDER_PEARL && config.disableEnderPearls()
                && combat.isTagged(event.getPlayer())) {
            event.setCancelled(true);
            config.send(event.getPlayer(), "enderpearl-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player
                && config.disableEnderPearls() && combat.isTagged(player)) {
            event.setCancelled(true);
            config.send(player, "enderpearl-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!combat.isTagged(event.getPlayer())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && config.disableEnderPearls()) {
            event.setCancelled(true);
            config.send(event.getPlayer(), "enderpearl-blocked");
            return;
        }
        if ((event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL)) {
            event.setCancelled(true);
            config.send(event.getPlayer(), "transfer-blocked");
            return;
        }
        if (config.blockProtectedRegions() && !regions.inside(event.getFrom()) && regions.inside(event.getTo())) {
            event.setCancelled(true);
            config.send(event.getPlayer(), "region-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (combat.isTagged(event.getPlayer())) {
            event.setCancelled(true);
            config.send(event.getPlayer(), "transfer-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!combat.isTagged(event.getPlayer()) || !config.blockProtectedRegions()
                || event.getTo() == null || regions.inside(event.getFrom()) || !regions.inside(event.getTo())) return;
        Vector push = event.getFrom().toVector().subtract(event.getTo().toVector());
        event.setTo(event.getFrom());
        if (push.lengthSquared() > 0) push.normalize().multiply(1.5);
        push.setY(0.3);
        Vector velocity = push;
        SchedulerUtil.execute(event.getPlayer(), () -> event.getPlayer().setVelocity(velocity));
        config.send(event.getPlayer(), "region-blocked");
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return player;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        if (damager instanceof Firework firework && firework.getShooter() instanceof Player player) return player;
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return player;
        if (damager instanceof EvokerFangs fangs && fangs.getOwner() instanceof Player player) return player;
        return null;
    }

    private void stealMoney(Player killer, Player victim) {
        if (!config.moneyStealEnabled() || plugin.getEconomyModule() == null
                || plugin.getEconomyModule().getEconomyService() == null) return;
        var economy = plugin.getEconomyModule().getEconomyService();
        double balance = economy.getBalance(victim.getUniqueId());
        if (balance <= 0.0) return;
        double stolen = balance * config.moneyStealPercentage() / 100.0;
        if (config.moneyStealMinimum() > 0.0 && stolen < config.moneyStealMinimum()) {
            stolen = Math.min(config.moneyStealMinimum(), balance);
        }
        if (config.moneyStealMaximum() > 0.0) stolen = Math.min(stolen, config.moneyStealMaximum());
        stolen = Math.min(stolen, balance);
        if (stolen <= 0.0 || !economy.withdraw(victim.getUniqueId(), stolen)) return;
        economy.deposit(killer.getUniqueId(), stolen);
        String amount = String.format(java.util.Locale.US, "%.2f", stolen);
        config.send(killer, "money-stolen-killer", "%amount%", amount, "%victim%", victim.getName());
        config.send(victim, "money-stolen-victim", "%amount%", amount, "%killer%", killer.getName());
    }
}
