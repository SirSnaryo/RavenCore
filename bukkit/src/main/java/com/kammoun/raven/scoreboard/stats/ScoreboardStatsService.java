package com.kammoun.raven.scoreboard.stats;

import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScoreboardStatsService {

    private final ScoreboardStatsRepository repository;
    private final Logger logger;
    private final Map<UUID, ScoreboardStats> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartedAt = new ConcurrentHashMap<>();

    public ScoreboardStatsService(ScoreboardStatsRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public void init() {
        if (repository == null) {
            return;
        }

        try {
            repository.init();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Scoreboard] Failed to create stats table.", e);
        }
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        sessionStartedAt.put(uuid, System.currentTimeMillis());

        if (repository == null) {
            cache.putIfAbsent(uuid, ScoreboardStats.empty());
            return;
        }

        SchedulerUtil.runAsync(() -> {
            try {
                ScoreboardStats loaded = repository.load(uuid);
                cache.merge(uuid, loaded, ScoreboardStats::mergeMax);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[Scoreboard] Failed to load stats for " + uuid, e);
            }
        });
    }

    public void recordKill(Player player) {
        UUID uuid = player.getUniqueId();
        cache.compute(uuid, (ignored, stats) -> (stats == null ? ScoreboardStats.empty() : stats).withKill());
        saveAsync(uuid);
    }

    public void recordDeath(Player player) {
        UUID uuid = player.getUniqueId();
        cache.compute(uuid, (ignored, stats) -> (stats == null ? ScoreboardStats.empty() : stats).withDeath());
        saveAsync(uuid);
    }

    public int kills(Player player) {
        return stats(player).kills();
    }

    public int deaths(Player player) {
        return stats(player).deaths();
    }

    public long playtimeSeconds(Player player) {
        UUID uuid = player.getUniqueId();
        ScoreboardStats stats = stats(player);
        return stats.playtimeSeconds() + sessionSeconds(uuid);
    }

    public void flushOnline() {
        Bukkit.getOnlinePlayers().forEach(this::flush);
    }

    public void unload(Player player) {
        flush(player);
        cache.remove(player.getUniqueId());
        sessionStartedAt.remove(player.getUniqueId());
    }

    public void shutdown() {
        flushOnline();
    }

    private ScoreboardStats stats(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), ignored -> ScoreboardStats.empty());
    }

    private void flush(Player player) {
        UUID uuid = player.getUniqueId();
        long totalSeconds = playtimeSeconds(player);
        cache.compute(uuid, (ignored, stats) ->
                (stats == null ? ScoreboardStats.empty() : stats).withPlaytimeSeconds(totalSeconds));
        sessionStartedAt.put(uuid, System.currentTimeMillis());
        saveAsync(uuid);
    }

    private long sessionSeconds(UUID uuid) {
        Long startedAt = sessionStartedAt.get(uuid);
        if (startedAt == null) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
    }

    private void saveAsync(UUID uuid) {
        if (repository == null) {
            return;
        }

        ScoreboardStats snapshot = cache.get(uuid);
        if (snapshot == null) {
            return;
        }

        SchedulerUtil.runAsync(() -> {
            try {
                repository.save(uuid, snapshot);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "[Scoreboard] Failed to save stats for " + uuid, e);
            }
        });
    }
}
