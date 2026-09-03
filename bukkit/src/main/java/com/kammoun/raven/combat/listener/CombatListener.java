package com.kammoun.raven.combat.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.combat.config.CombatConfig;
import com.kammoun.raven.combat.service.CombatService;
import com.kammoun.raven.combat.service.ProtectedRegionChecker;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Material;
import org.bukkit.Location;
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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class CombatListener implements Listener {
    public static final String LOGOUT_DEATH_MESSAGE_METADATA = "raven_combat_logout_death_message";

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

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combat.isTagged(player) || plugin.getCombatModule().isServerStopping()) return;
        if (plugin.getCombatModule().isNetworkTransfer(player.getUniqueId())) {
            plugin.getCombatModule().unloadLocal(player.getUniqueId());
            return;
        }
        combat.remove(player.getUniqueId(), false, true);
        if (!config.killOnLogout()) return;
        player.setMetadata(LOGOUT_DEATH_MESSAGE_METADATA,
                new FixedMetadataValue(plugin, config.message("logout-death", "%player%", player.getName())));
        dropInventory(player);
        player.setHealth(0.0);
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
        if (item != null && item.getType() == Material.ENDER_PEARL && config.blockVanillaEnderPearls()
                && combat.isTagged(event.getPlayer())) {
            event.setCancelled(true);
            config.send(event.getPlayer(), "enderpearl-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player
                && config.blockVanillaEnderPearls() && combat.isTagged(player)) {
            event.setCancelled(true);
            config.send(player, "enderpearl-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!combat.isTagged(event.getPlayer())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && config.blockVanillaEnderPearls()) {
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

    private void dropInventory(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) return;
        for (ItemStack item : player.getInventory().getContents()) {
            drop(location, item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            drop(location, item);
        }
        drop(location, player.getInventory().getItemInOffHand());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }

    private void drop(Location location, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
        location.getWorld().dropItemNaturally(location, item.clone());
    }

}
