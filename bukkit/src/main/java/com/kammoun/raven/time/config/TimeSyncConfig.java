package com.kammoun.raven.time.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class TimeSyncConfig {

    private final boolean enabled;
    private final int broadcastIntervalTicks;
    private final int heartbeatSeconds;
    private final int lockTtlSeconds;
    private final boolean syncWeather;
    private final boolean syncManualWeatherCommands;
    private final boolean spawnFreezeTime;
    private final long spawnDefaultTime;
    private final boolean spawnFreezeWeatherCycle;

    private TimeSyncConfig(boolean enabled, int broadcastIntervalTicks, int heartbeatSeconds, int lockTtlSeconds,
                           boolean syncWeather, boolean syncManualWeatherCommands, boolean spawnFreezeTime,
                           long spawnDefaultTime, boolean spawnFreezeWeatherCycle) {
        this.enabled = enabled;
        this.broadcastIntervalTicks = broadcastIntervalTicks;
        this.heartbeatSeconds = heartbeatSeconds;
        this.lockTtlSeconds = lockTtlSeconds;
        this.syncWeather = syncWeather;
        this.syncManualWeatherCommands = syncManualWeatherCommands;
        this.spawnFreezeTime = spawnFreezeTime;
        this.spawnDefaultTime = spawnDefaultTime;
        this.spawnFreezeWeatherCycle = spawnFreezeWeatherCycle;
    }

    public static TimeSyncConfig load(FileConfiguration config) {
        return new TimeSyncConfig(
                config.getBoolean("enabled", true),
                config.getInt("broadcast-interval-ticks", 20),
                config.getInt("heartbeat-seconds", 5),
                config.getInt("lock-ttl-seconds", 12),
                config.getBoolean("weather.sync-automatically", false),
                config.getBoolean("weather.sync-manual-commands", true),
                config.getBoolean("spawn.freeze-time", true),
                Math.floorMod(config.getLong("spawn.default-time", 1000L), 24000L),
                config.getBoolean("spawn.freeze-weather-cycle", true)
        );
    }

    public boolean enabled() { return enabled; }
    public int broadcastIntervalTicks() { return broadcastIntervalTicks; }
    public int heartbeatSeconds() { return heartbeatSeconds; }
    public int lockTtlSeconds() { return lockTtlSeconds; }
    public boolean syncWeather() { return syncWeather; }
    public boolean syncManualWeatherCommands() { return syncManualWeatherCommands; }
    public boolean spawnFreezeTime() { return spawnFreezeTime; }
    public long spawnDefaultTime() { return spawnDefaultTime; }
    public boolean spawnFreezeWeatherCycle() { return spawnFreezeWeatherCycle; }
}
