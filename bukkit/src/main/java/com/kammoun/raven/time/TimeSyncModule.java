package com.kammoun.raven.time;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.redis.event.TimeWeatherSyncEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.time.config.TimeSyncConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the overworld time of day and weather identical across every survival server.
 *
 * <p>One server is elected the authority via {@link TimeAuthority} (a short-lived Redis lock). The
 * authority runs the normal daylight/weather cycles and broadcasts its clock every interval; every
 * other survival server turns its own cycles off and mirrors the broadcast. If the authority goes
 * down, the lock expires and another server takes over automatically — which is why the authority
 * re-asserts its gamerules each tick (undoing the follower state it may have been left in).
 */
public class TimeSyncModule extends KModule<RavenCore> {

    private TimeSyncConfig config;
    private TimeAuthority authority;
    private WorldClockStore clockStore;

    private final AtomicReference<World> overworld = new AtomicReference<>();
    private SchedulerUtil.Task heartbeatTask;
    private SchedulerUtil.Task driverTask;

    public TimeSyncModule() {
        super("time");
    }

    @Override
    public void onModuleEnable() {
        config = TimeSyncConfig.load(getConfig());
        if (!config.enabled()) {
            getLogger().info("[TimeSync] Disabled in config.");
            return;
        }

        JedisPool pool = api.getRedisMessenger().getConnection().getPool();
        authority = new TimeAuthority(api, pool, api.getServerId(), config.lockTtlSeconds());
        clockStore = new WorldClockStore(api, pool);

        resolveOverworld();

        // A follower a player logs into should already show the right time, not wait for a broadcast.
        onEvent(PlayerJoinEvent.class, event -> pullAndApply());

        api.getNetworkEventBus().subscribe(TimeWeatherSyncEvent.class, event -> {
            if (api.getServerId().equals(event.getSourceServer())) return; // ignore our own broadcast
            applyClock(fromEvent(event));
        });

        long heartbeatTicks = Math.max(1, config.heartbeatSeconds()) * 20L;
        heartbeatTask = SchedulerUtil.runAsyncTimer(() -> authority.heartbeat(), 0L, heartbeatTicks);

        long intervalTicks = Math.max(1, config.broadcastIntervalTicks());
        driverTask = SchedulerUtil.runTimer(this::drive, intervalTicks, intervalTicks);

        getLogger().info("[TimeSync] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (driverTask != null) driverTask.cancel();
        if (authority != null) {
            api.getNetworkEventBus().unsubscribe(TimeWeatherSyncEvent.class);
            authority.release();
        }
        getLogger().info("[TimeSync] Disabled.");
    }

    // -------------------------------------------------------------------------

    /**
     * Runs every broadcast interval; only the authority publishes. This runs on the global region
     * thread (Folia) / main thread (Bukkit) — the only thread allowed to touch world-wide settings
     * (gamerules, time, weather) on Canvas/Folia, so we mutate the world directly here.
     */
    private void drive() {
        if (!authority.isAuthority()) return;
        World world = overworld.get();
        if (world == null) return;

        // Re-assert the live cycles in case this server was a follower before winning the lock.
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);

        WorldClockState state = new WorldClockState(
                world.getFullTime(), world.hasStorm(), world.isThundering(),
                world.getWeatherDuration(), world.getThunderDuration());

        SchedulerUtil.runAsync(() -> {
            clockStore.write(state);
            api.getNetworkEventBus().publish(toEvent(state));
        });
    }

    /** Pulls the current clock from Redis and applies it — used on player join (followers only). */
    private void pullAndApply() {
        if (authority.isAuthority()) return;
        SchedulerUtil.runAsync(() -> clockStore.read().ifPresent(state ->
                SchedulerUtil.run(() -> applyClock(state))));
    }

    /**
     * Mirrors the authority's clock locally. A no-op on the authority itself. Must be called on the
     * global region / main thread (the event-bus dispatcher and {@link #pullAndApply()} both are),
     * since gamerule/time/weather mutation isn't allowed off the global region on Canvas/Folia.
     */
    private void applyClock(WorldClockState state) {
        if (authority.isAuthority()) return;
        World world = overworld.get();
        if (world == null) return;

        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setFullTime(state.fullTime);
        world.setStorm(state.storm);
        world.setThundering(state.thunder);
        world.setWeatherDuration(state.weatherDuration);
        world.setThunderDuration(state.thunderDuration);
    }

    /**
     * Resolves this server's overworld. Plugins load at STARTUP so the world may not be loaded yet —
     * mirror {@code RtpModule}: use it if already present, otherwise latch onto its WorldLoadEvent.
     */
    private void resolveOverworld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                overworld.set(world);
                return;
            }
        }
        onEvent(WorldLoadEvent.class, event -> {
            if (overworld.get() == null && event.getWorld().getEnvironment() == World.Environment.NORMAL) {
                overworld.set(event.getWorld());
            }
        });
    }

    private static WorldClockState fromEvent(TimeWeatherSyncEvent e) {
        return new WorldClockState(e.fullTime, e.storm, e.thunder, e.weatherDuration, e.thunderDuration);
    }

    private static TimeWeatherSyncEvent toEvent(WorldClockState s) {
        return new TimeWeatherSyncEvent(s.fullTime, s.storm, s.thunder, s.weatherDuration, s.thunderDuration);
    }
}
