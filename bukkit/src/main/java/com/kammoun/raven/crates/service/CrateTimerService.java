package com.kammoun.raven.crates.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.config.CratesConfig;
import com.kammoun.raven.crates.db.CrateKeyRepository;
import com.kammoun.raven.crates.db.CrateTimerRepository;
import com.kammoun.raven.crates.db.CrateTimerRepository.AdvanceResult;
import com.kammoun.raven.crates.db.CrateTimerRepository.TimerState;
import com.kammoun.raven.crates.player.CratePlayerManager;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CrateTimerService {
    private final RavenCore plugin;
    private final CratesConfig config;
    private final CrateTimerRepository timerRepository;
    private final CrateKeyRepository keyRepository;
    private final CratePlayerManager players;
    private final ConcurrentMap<UUID, Long> localGrantedCycles = new ConcurrentHashMap<>();
    private volatile long nextRewardAt;
    private volatile long cycleId;
    private SchedulerUtil.Task task;

    public CrateTimerService(RavenCore plugin, CratesConfig config, CrateTimerRepository timerRepository,
                             CrateKeyRepository keyRepository, CratePlayerManager players) {
        this.plugin = plugin;
        this.config = config;
        this.timerRepository = timerRepository;
        this.keyRepository = keyRepository;
        this.players = players;
    }

    public void start() {
        stop();
        task = SchedulerUtil.runAsyncTimer(this::tickSafely, 20L, config.timerCheckSeconds() * 20L);
        tickSafely();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            plugin.getLogger().warning("[Crates] Timer tick failed: " + e.getMessage());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        TimerState state = timerRepository.getOrCreate(now, config.timerIntervalSeconds());
        nextRewardAt = state.nextRewardAt();
        cycleId = state.cycleId();
        if (now < state.nextRewardAt()) return;

        AdvanceResult advanced = timerRepository.tryAdvance(state.nextRewardAt(), now, config.timerIntervalSeconds());
        if (advanced == null) {
            TimerState refreshed = timerRepository.getOrCreate(now, config.timerIntervalSeconds());
            nextRewardAt = refreshed.nextRewardAt();
            cycleId = refreshed.cycleId();
            grantCycle(refreshed.cycleId());
            return;
        }
        nextRewardAt = advanced.nextRewardAt();
        cycleId = advanced.cycleId();
        grantCycle(advanced.cycleId());
    }

    private void grantCycle(long cycle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Long previous = localGrantedCycles.put(uuid, cycle);
            if (previous != null && previous == cycle) continue;
            try {
                if (!timerRepository.claimPlayerCycle(uuid, cycle)) continue;
                keyRepository.addKeys(uuid, config.timerCrateId(), config.timerAmount());
                players.add(uuid, config.timerCrateId(), config.timerAmount());
                SchedulerUtil.execute(player, () -> player.sendMessage(ChatFormatter.color(config.receivedKeyMessage()
                        .replace("%amount%", String.valueOf(config.timerAmount()))
                        .replace("%crate%", config.timerCrateId()))));
            } catch (Exception e) {
                plugin.getLogger().warning("[Crates] Failed to grant timed key to " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public long secondsLeft() {
        long left = nextRewardAt - System.currentTimeMillis();
        return Math.max(0L, left / 1000L);
    }

    public String formattedTimeLeft() {
        long seconds = secondsLeft();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format("%02d:%02d:%02d", hours, minutes, secs);
        return String.format("%02d:%02d", minutes, secs);
    }

    public long cycleId() {
        return cycleId;
    }
}
