package com.kammoun.raven.leaderboard.service;

import com.kammoun.raven.leaderboard.config.LeaderboardConfig;
import com.kammoun.raven.leaderboard.model.LeaderboardEntry;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.util.NumberUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-rendered leaderboard positions for PlaceholderAPI, so a placeholder lookup is an
 * array read instead of a list scan plus number formatting. Rebuilt from the leaderboard
 * cache after each leaderboard refresh, never from the database.
 */
public final class LeaderboardPlaceholderCache {
    private record Snapshot(String[] names, String[] values, String[] raw) {}

    private final LeaderboardService service;
    private final Map<LeaderboardType, Snapshot> snapshots = new ConcurrentHashMap<>();
    private volatile LeaderboardConfig config;

    public LeaderboardPlaceholderCache(LeaderboardService service, LeaderboardConfig config) {
        this.service = service;
        this.config = config;
    }

    public void start() {
        service.setRefreshListener(this::rebuild);
    }

    public void updateConfig(LeaderboardConfig config) {
        this.config = config;
        rebuild();
    }

    public void shutdown() {
        snapshots.clear();
    }

    public String name(LeaderboardType type, int position) {
        Snapshot snapshot = snapshots.get(type);
        if (snapshot == null || position < 1 || position > snapshot.names().length) {
            return config.placeholderEmptyName();
        }
        return snapshot.names()[position - 1];
    }

    public String value(LeaderboardType type, int position) {
        Snapshot snapshot = snapshots.get(type);
        if (snapshot == null || position < 1 || position > snapshot.values().length) {
            return config.placeholderEmptyValue();
        }
        return snapshot.values()[position - 1];
    }

    public String raw(LeaderboardType type, int position) {
        Snapshot snapshot = snapshots.get(type);
        if (snapshot == null || position < 1 || position > snapshot.raw().length) {
            return config.placeholderEmptyValue();
        }
        return snapshot.raw()[position - 1];
    }

    private synchronized void rebuild() {
        Set<LeaderboardType> enabled = config.enabledTypes();
        for (LeaderboardType type : enabled) {
            List<LeaderboardEntry> entries = service.entries(type);
            int size = entries.size();
            String[] names = new String[size];
            String[] values = new String[size];
            String[] raw = new String[size];
            for (int i = 0; i < size; i++) {
                LeaderboardEntry entry = entries.get(i);
                names[i] = entry.playerName();
                values[i] = service.format(type, entry.value());
                raw[i] = type.decimal()
                        // plain string, so large balances never come out as 1.23E7
                        ? BigDecimal.valueOf(NumberUtils.round(entry.value(), 2)).stripTrailingZeros().toPlainString()
                        : Long.toString((long) entry.value());
            }
            snapshots.put(type, new Snapshot(names, values, raw));
        }
        snapshots.keySet().retainAll(enabled);
    }
}
