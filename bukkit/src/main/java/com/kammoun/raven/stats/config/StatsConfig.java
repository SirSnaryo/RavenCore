package com.kammoun.raven.stats.config;

import com.kammoun.raven.stats.model.StatsType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

public final class StatsConfig {
    private final long flushIntervalTicks;
    private final long refreshIntervalTicks;
    private final int databaseWorkers;
    private final int databaseQueueCapacity;
    private final int detachedWriteCapacity;
    private final int batchSize;
    private final Set<StatsType> tracked = EnumSet.noneOf(StatsType.class);

    public StatsConfig(FileConfiguration config) {
        flushIntervalTicks = Math.max(20L, config.getLong("flush-interval-seconds", 10L) * 20L);
        refreshIntervalTicks = Math.max(200L, config.getLong("refresh-interval-seconds", 60L) * 20L);
        databaseWorkers = Math.max(1, Math.min(4, config.getInt("database-workers", 2)));
        databaseQueueCapacity = Math.max(1000, config.getInt("database-queue-capacity", 4096));
        detachedWriteCapacity = Math.max(1000, config.getInt("detached-write-capacity", databaseQueueCapacity));
        batchSize = Math.max(50, Math.min(500, config.getInt("database-batch-size", 250)));
        for (StatsType type : StatsType.values()) {
            if (type == StatsType.MONEY_MADE || type == StatsType.MONEY_SPENT) continue;
            if (config.getBoolean("tracking." + type.column().replace('_', '-'), true)) tracked.add(type);
        }
        if (config.getBoolean("tracking.money-changes", true)) {
            tracked.add(StatsType.MONEY_MADE);
            tracked.add(StatsType.MONEY_SPENT);
        }
    }

    public boolean tracks(StatsType type) { return tracked.contains(type); }
    public long flushIntervalTicks() { return flushIntervalTicks; }
    public long refreshIntervalTicks() { return refreshIntervalTicks; }
    public int databaseWorkers() { return databaseWorkers; }
    public int databaseQueueCapacity() { return databaseQueueCapacity; }
    public int detachedWriteCapacity() { return detachedWriteCapacity; }
    public int batchSize() { return batchSize; }
}
