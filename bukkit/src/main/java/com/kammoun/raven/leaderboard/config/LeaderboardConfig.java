package com.kammoun.raven.leaderboard.config;

import com.kammoun.raven.leaderboard.model.LeaderboardType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

public final class LeaderboardConfig {
    private final long refreshIntervalTicks;
    private final int cacheSize;
    private final int playersPerPage;
    private final Set<LeaderboardType> enabledTypes;

    public LeaderboardConfig(FileConfiguration config) {
        long refreshSeconds = config.contains("refresh-rate")
                ? config.getLong("refresh-rate", 300L)
                : config.getLong("refresh-interval-seconds", 300L);
        refreshIntervalTicks = Math.max(1_200L, refreshSeconds * 20L);
        cacheSize = Math.max(10, Math.min(1000, config.getInt("cache-size", 100)));
        playersPerPage = Math.max(1, Math.min(45, config.getInt("players-per-page", 9)));
        enabledTypes = EnumSet.noneOf(LeaderboardType.class);
        for (String raw : config.getStringList("enabled-types")) {
            LeaderboardType type = LeaderboardType.parse(raw);
            if (type != null) enabledTypes.add(type);
        }
        if (enabledTypes.isEmpty()) {
            enabledTypes.addAll(EnumSet.allOf(LeaderboardType.class));
        }
    }

    public long refreshIntervalTicks() { return refreshIntervalTicks; }
    public int cacheSize() { return cacheSize; }
    public int playersPerPage() { return playersPerPage; }
    public Set<LeaderboardType> enabledTypes() { return Set.copyOf(enabledTypes); }
    public boolean enabled(LeaderboardType type) { return enabledTypes.contains(type); }
}
