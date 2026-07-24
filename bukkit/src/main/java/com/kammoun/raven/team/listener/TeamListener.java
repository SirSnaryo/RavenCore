package com.kammoun.raven.team.listener;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.team.config.TeamMessages;
import com.kammoun.raven.team.model.TeamPermission;
import com.kammoun.raven.team.model.TeamSnapshot;
import com.kammoun.raven.team.service.TeamService;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.TeamChatEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class TeamListener implements Listener {
    private final RavenCore plugin;
    private final TeamService service;
    private final TeamMessages messages;
    private final String chatFormat;

    public TeamListener(RavenCore plugin, TeamService service, TeamMessages messages, String chatFormat) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
        this.chatFormat = chatFormat;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!service.isTeamChat(sender.getUniqueId())) return;
        TeamSnapshot team = service.cached(sender.getUniqueId()).orElse(null);
        if (team == null) return;
        event.setCancelled(true);
        TeamChatEvent message = new TeamChatEvent();
        message.setTeamId(team.id().toString());
        message.setSenderUuid(sender.getUniqueId().toString());
        message.setSenderName(sender.getName());
        message.setMessage(event.getMessage());
        plugin.getNetworkEventBus().publish(message);
    }

    public void deliver(TeamChatEvent event) {
        UUID teamId;
        try {
            teamId = UUID.fromString(event.getTeamId());
        } catch (RuntimeException ignored) {
            return;
        }
        String line = ChatFormatter.color(chatFormat
                .replace("%player%", event.getSenderName())
                .replace("%message%", event.getMessage()));
        for (Player receiver : plugin.getServer().getOnlinePlayers()) {
            TeamSnapshot team = service.cached(receiver.getUniqueId()).orElse(null);
            if (team != null && team.id().equals(teamId)) {
                SchedulerUtil.execute(receiver, () -> {
                    if (receiver.isOnline()) receiver.sendMessage(line);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        TeamSnapshot attackerTeam = service.cached(attacker.getUniqueId()).orElse(null);
        TeamSnapshot victimTeam = service.cached(victim.getUniqueId()).orElse(null);
        if (attackerTeam != null && victimTeam != null && attackerTeam.id().equals(victimTeam.id())
                && !attackerTeam.pvpEnabled()) {
            event.setCancelled(true);
            messages.send(attacker, "friendly-fire-disabled");
        }
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }
}
