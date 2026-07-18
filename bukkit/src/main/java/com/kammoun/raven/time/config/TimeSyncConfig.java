package com.kammoun.raven.time.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Typed view of time/config.yml, loaded the same way as {@code RtpPoolConfig}. */
public final class TimeSyncConfig {

    private final boolean enabled;
    private final int broadcastIntervalTicks;
    private final int heartbeatSeconds;
    private final int lockTtlSeconds;

    private TimeSyncConfig(boolean enabled, int broadcastIntervalTicks, int heartbeatSeconds, int lockTtlSeconds) {
        this.enabled = enabled;
        this.broadcastIntervalTicks = broadcastIntervalTicks;
        this.heartbeatSeconds = heartbeatSeconds;
        this.lockTtlSeconds = lockTtlSeconds;
    }

    public static TimeSyncConfig load(FileConfiguration config) {
        return new TimeSyncConfig(
                config.getBoolean("enabled", true),
                config.getInt("broadcast-interval-ticks", 20),
                config.getInt("heartbeat-seconds", 5),
                config.getInt("lock-ttl-seconds", 12)
        );
    }

    public boolean enabled() { return enabled; }
    public int broadcastIntervalTicks() { return broadcastIntervalTicks; }
    public int heartbeatSeconds() { return heartbeatSeconds; }
    public int lockTtlSeconds() { return lockTtlSeconds; }
}
