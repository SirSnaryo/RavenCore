package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.rtp.config.RtpConfig;
import com.kammoun.raven.rtp.config.RtpMessage;
import com.kammoun.raven.rtp.config.RtpMessages;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RtpWarmupManager implements Listener {
    private final RavenCore plugin;
    private final RtpConfig config;
    private final RtpMessages messages;
    private final RtpSounds sounds;
    private final Map<UUID, ScheduledTask> active = new ConcurrentHashMap<>();

    public RtpWarmupManager(RavenCore plugin, RtpConfig config, RtpMessages messages, RtpSounds sounds) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
    }

    public boolean isActive(UUID uuid) {
        return active.containsKey(uuid);
    }

    public void begin(Player player, Runnable complete) {
        player.closeInventory();
        if (config.warmupSeconds() <= 0) {
            complete.run();
            return;
        }
        UUID uuid = player.getUniqueId();
        AtomicInteger secondsLeft = new AtomicInteger(config.warmupSeconds());
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            int remaining = secondsLeft.getAndDecrement();
            if (remaining <= 0) {
                scheduled.cancel();
                if (active.remove(uuid, scheduled) && player.isOnline()) {
                    complete.run();
                }
                return;
            }
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    messages.get(RtpMessage.WARMUP_ACTIONBAR,
                            "%seconds%", String.valueOf(remaining))));
            sounds.play(player, "warmup-start");
        }, () -> active.remove(uuid), 1L, 20L);
        ScheduledTask previous = active.put(player.getUniqueId(), task);
        if (previous != null) previous.cancel();
    }

    public void stop() {
        active.values().forEach(ScheduledTask::cancel);
        active.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.cancelOnMove() || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        cancel(event.getPlayer(), RtpMessage.CANCELLED_MOVE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (config.cancelOnDamage() && event.getEntity() instanceof Player player) {
            cancel(player, RtpMessage.CANCELLED_DAMAGE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ScheduledTask task = active.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    private void cancel(Player player, RtpMessage reason) {
        ScheduledTask task = active.remove(player.getUniqueId());
        if (task == null) return;
        task.cancel();
        messages.send(player, reason);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                messages.get(reason)));
        sounds.play(player, "warmup-cancelled");
    }
}
