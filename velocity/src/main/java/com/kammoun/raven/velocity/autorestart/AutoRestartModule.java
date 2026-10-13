package com.kammoun.raven.velocity.autorestart;

import com.kammoun.raven.library.module.VModule;
import com.kammoun.raven.library.redis.event.ServerRestartEvent;
import com.kammoun.raven.velocity.RavenVelocity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AutoRestartModule extends VModule {

    private static final LegacyComponentSerializer COLOR = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    private ScheduledTask task;
    private ZoneId zone;
    private LocalTime restartTime;
    private LocalDate lastRunDay;

    public AutoRestartModule() {
        super("autorestart");
    }

    @Override
    public void onModuleEnable() {
        if (!getBoolean("enabled", true)) {
            getLogger().info("[AutoRestart] Disabled by config.");
            return;
        }
        zone = parseZone(getString("timezone", "Asia/Riyadh"));
        restartTime = parseTime(getString("restart-time", "03:00"));
        if (!ZonedDateTime.now(zone).isBefore(LocalDate.now(zone).atTime(restartTime).atZone(zone))) {
            lastRunDay = LocalDate.now(zone);
        }
        task = server.getScheduler().buildTask(plugin, this::tick)
                .repeat(1L, TimeUnit.SECONDS)
                .schedule();
        getLogger().info("[AutoRestart] Daily restart at " + restartTime + " (" + zone + ").");
    }

    @Override
    public void reload() {
        super.reload();
        zone = parseZone(getString("timezone", "Asia/Riyadh"));
        restartTime = parseTime(getString("restart-time", "03:00"));
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = LocalDate.now(zone).atTime(restartTime).atZone(zone);
        lastRunDay = next.isAfter(now) ? null : LocalDate.now(zone);
    }

    @Override
    public void onModuleDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atTime(restartTime).atZone(zone);
        if (!next.isAfter(now)) {
            if (!now.toLocalDate().equals(lastRunDay)) {
                lastRunDay = now.toLocalDate();
                restart();
            }
            return;
        }
        long seconds = Duration.between(now, next).getSeconds();
        long warmupSeconds = Math.max(1, getInt("warmup-minutes", 5)) * 60L;
        if (seconds > warmupSeconds) {
            return;
        }
        if (seconds % 60L == 0L || seconds == 30L || seconds == 10L) {
            broadcast(timeText(seconds), false);
        }
    }

    private String timeText(long seconds) {
        if (seconds >= 60L) {
            long minutes = seconds / 60L;
            return minutes + (minutes == 1L ? " Minute" : " Minutes");
        }
        return seconds + (seconds == 1L ? " Second" : " Seconds");
    }

    private void broadcast(String time, boolean now) {
        String chat = now
                ? getString("restart-now-message", "&#FF2B2BRaven SMP &8\u2192 &fRestarting now")
                : getString("restart-message", "&#FF2B2BRaven SMP &8\u2192 &fRestarting in &#FF2B2B%time%")
                        .replace("%time%", time);
        String subtitle = now
                ? getString("title-sub-now", "&fRestarting now")
                : getString("title-sub", "&fRestarting in &#FF2B2B%time%").replace("%time%", time);
        Component chatComponent = COLOR.deserialize(chat);
        Title title = Title.title(COLOR.deserialize(getString("title-main", "&#FF2B2BRaven SMP")),
                COLOR.deserialize(subtitle));
        String soundKey = getString("sound", "block.note_block.pling");
        for (Player player : server.getAllPlayers()) {
            player.sendMessage(chatComponent);
            player.showTitle(title);
            if (soundKey != null && !soundKey.isBlank()) {
                player.playSound(Sound.sound(Key.key(soundKey.toLowerCase(Locale.ROOT)),
                        Sound.Source.MASTER, 1.0f, 1.0f));
            }
        }
    }

    private void restart() {
        broadcast(null, true);
        Component kick = COLOR.deserialize(getString("kick-message",
                "&#FF2B2BRaven SMP &8\u2192 &fThe server is restarting. You can rejoin in a moment!"));
        for (Player player : server.getAllPlayers()) {
            player.disconnect(kick);
        }
        try {
            if (plugin instanceof RavenVelocity velocity && velocity.getNetworkEventBus() != null) {
                ServerRestartEvent event = new ServerRestartEvent();
                event.setSourceServer(velocity.getServerId());
                event.setTargetServer("ALL");
                velocity.getNetworkEventBus().publish(event);
            }
        } catch (Exception error) {
            getLogger().warning("[AutoRestart] Failed to notify backend servers: " + error.getMessage());
        }
        int delay = Math.max(1, getInt("proxy-shutdown-delay-seconds", 20));
        server.getScheduler().buildTask(plugin, () -> {
            String command = getString("restart-command", "");
            if (command != null && !command.isBlank()) {
                try {
                    new ProcessBuilder("/bin/sh", "-c", command).start();
                } catch (Exception error) {
                    getLogger().warning("[AutoRestart] Failed to run restart command: " + error.getMessage());
                }
            }
            server.shutdown();
        }).delay(delay, TimeUnit.SECONDS).schedule();
        getLogger().info("[AutoRestart] Restart sequence started.");
    }

    private ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value);
        } catch (Exception error) {
            getLogger().warning("[AutoRestart] Invalid timezone '" + value + "', falling back to Asia/Riyadh.");
            return ZoneId.of("Asia/Riyadh");
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception error) {
            getLogger().warning("[AutoRestart] Invalid restart-time '" + value + "', falling back to 03:00.");
            return LocalTime.of(3, 0);
        }
    }
}
