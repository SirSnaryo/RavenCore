package com.kammoun.raven.leaderboard.service;

import com.kammoun.raven.leaderboard.config.LeaderboardConfig;
import com.kammoun.raven.leaderboard.db.LeaderboardRepository;
import com.kammoun.raven.leaderboard.model.LeaderboardEntry;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.library.util.NumberUtils;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class LeaderboardService {
    private final LeaderboardRepository repository;
    private final Logger logger;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Map<LeaderboardType, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();
    private volatile LeaderboardConfig config;
    /**
     * -- SETTER --
     * Runs on the async refresh thread once every cache refresh finishes.
     */
    @Setter
    private volatile Runnable refreshListener;
    private SchedulerUtil.Task refreshTask;

    public LeaderboardService(LeaderboardRepository repository, LeaderboardConfig config, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.logger = logger;
    }

    public void start() {
        refresh();
        schedule();
    }

    public void updateConfig(LeaderboardConfig config) {
        this.config = config;
        schedule();
        refresh();
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        refreshListener = null;
        cache.clear();
    }

    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        SchedulerUtil.runAsync(() -> {
            try {
                for (LeaderboardType type : config.enabledTypes()) {
                    try {
                        cache.put(type, repository.top(type, config.cacheSize()));
                    } catch (Exception e) {
                        logger.warning("[Leaderboard] Failed to refresh " + type.key() + ": " + e.getMessage());
                    }
                }
            } finally {
                refreshing.set(false);
            }
            Runnable listener = refreshListener;
            if (listener == null) return;
            try {
                listener.run();
            } catch (Exception e) {
                logger.warning("[Leaderboard] Refresh listener failed: " + e.getMessage());
            }
        });
    }

    public List<LeaderboardEntry> entries(LeaderboardType type) {
        return cache.getOrDefault(type, Collections.emptyList());
    }

    public LeaderboardEntry findPlayer(LeaderboardType type, UUID uuid) {
        for (LeaderboardEntry entry : entries(type)) {
            if (entry.uuid().equals(uuid)) return entry;
        }
        return null;
    }

    public int maxPage(LeaderboardType type) {
        int size = entries(type).size();
        return Math.max(1, (int) Math.ceil(size / (double) config.playersPerPage()));
    }

    public int playersPerPage() {
        return config.playersPerPage();
    }

    public boolean enabled(LeaderboardType type) {
        return config.enabled(type);
    }

    public String format(LeaderboardType type, double value) {
        if (type == LeaderboardType.PLAY_TIME) {
            return formatDuration((long) value);
        }
        return type.decimal() ? NumberUtils.format(value) : NumberUtils.format((long) value);
    }

    private void schedule() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = SchedulerUtil.runAsyncTimer(this::refresh,
                config.refreshIntervalTicks(), config.refreshIntervalTicks());
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
