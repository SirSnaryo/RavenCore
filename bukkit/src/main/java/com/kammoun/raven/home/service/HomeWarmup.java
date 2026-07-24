package com.kammoun.raven.home.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.home.config.HomeConfig;
import com.kammoun.raven.home.config.HomeMessages;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class HomeWarmup {
    private final RavenCore plugin;
    private final HomeConfig config;
    private final HomeMessages messages;
    private final Map<UUID, ScheduledTask> active = new ConcurrentHashMap<>();

    public HomeWarmup(RavenCore plugin, HomeConfig config, HomeMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    public void begin(Player player, String homeName, Runnable completion) {
        player.closeInventory();
        cancelTask(player.getUniqueId());
        if (config.warmupSeconds() == 0) {
            completion.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(config.warmupSeconds());
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            int seconds = remaining.getAndDecrement();
            if (seconds <= 0) {
                scheduled.cancel();
                if (active.remove(player.getUniqueId(), scheduled) && player.isOnline()) completion.run();
                return;
            }
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    messages.get("warmup-actionbar", "%home%", homeName, "%seconds%", String.valueOf(seconds))));
        }, () -> active.remove(player.getUniqueId()), 1L, 20L);
        active.put(player.getUniqueId(), task);
    }

    public void onMove(PlayerMoveEvent event) {
        if (!config.cancelOnMove() || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        cancel(event.getPlayer(), "cancelled-move");
    }

    public void onDamage(EntityDamageEvent event) {
        if (config.cancelOnDamage() && event.getEntity() instanceof Player player) cancel(player, "cancelled-damage");
    }

    public void onQuit(PlayerQuitEvent event) {
        cancelTask(event.getPlayer().getUniqueId());
    }

    public void stop() {
        active.values().forEach(ScheduledTask::cancel);
        active.clear();
    }

    private void cancel(Player player, String message) {
        ScheduledTask task = active.remove(player.getUniqueId());
        if (task == null) return;
        task.cancel();
        messages.send(player, message);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(messages.get(message)));
    }

    private void cancelTask(UUID uuid) {
        ScheduledTask previous = active.remove(uuid);
        if (previous != null) previous.cancel();
    }
}
