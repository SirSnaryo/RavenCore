package com.kammoun.raven.time;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.library.redis.event.TimeWeatherSyncEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.time.config.TimeSyncConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import redis.clients.jedis.JedisPool;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TimeSyncModule extends KModule<RavenCore> {

    private TimeSyncConfig config;
    private TimeAuthority authority;
    private WorldClockStore clockStore;
    private String scope;

    private final AtomicReference<World> overworld = new AtomicReference<>();
    private final AtomicLong spawnFrozenTime = new AtomicLong(1000L);
    private SchedulerUtil.Task heartbeatTask;
    private SchedulerUtil.Task driverTask;
    private final AtomicBoolean applyingClock = new AtomicBoolean(false);

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

        scope = scope();
        spawnFrozenTime.set(config.spawnDefaultTime());
        JedisPool pool = api.getRedisMessenger().getConnection().getPool();
        authority = new TimeAuthority(api, pool, api.getServerId(), config.lockTtlSeconds(), scope);
        clockStore = new WorldClockStore(api, pool, scope);

        resolveOverworld();
        onEvent(PlayerJoinEvent.class, event -> pullAndApply());
        onEvent(WorldLoadEvent.class, event -> {
            if (event.getWorld().getEnvironment() == World.Environment.NORMAL) {
                overworld.compareAndSet(null, event.getWorld());
                SchedulerUtil.run(() -> applyLocalPolicy(event.getWorld()));
            }
        });
        onEvent(PlayerCommandPreprocessEvent.class, EventPriority.MONITOR, this::onCommand);
        onEvent(ServerCommandEvent.class, EventPriority.MONITOR, this::onServerCommand);
        onEvent(TimeSkipEvent.class, EventPriority.MONITOR, this::onTimeSkip);
        onEvent(WeatherChangeEvent.class, EventPriority.MONITOR, this::onWeatherChange);
        onEvent(ThunderChangeEvent.class, EventPriority.MONITOR, this::onThunderChange);

        api.getNetworkEventBus().subscribe(TimeWeatherSyncEvent.class, event -> {
            if (api.getServerId().equals(event.getSourceServer())) return;
            if (!scope.equalsIgnoreCase(event.scope == null ? "survival" : event.scope)) return;
            applyClock(fromEvent(event));
        });

        long heartbeatTicks = Math.max(1, config.heartbeatSeconds()) * 20L;
        heartbeatTask = SchedulerUtil.runAsyncTimer(() -> authority.heartbeat(), 0L, heartbeatTicks);

        long intervalTicks = Math.max(1, config.broadcastIntervalTicks());
        driverTask = SchedulerUtil.runTimer(this::drive, intervalTicks, intervalTicks);

        getLogger().info("[TimeSync] Enabled for scope " + scope + ".");
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

    private void drive() {
        World world = overworld.get();
        if (world == null) return;

        if (api.getServerRole() == ServerRole.SPAWN) {
            applySpawnFreeze(world);
        }

        if (!authority.isAuthority()) return;
        applyAuthorityPolicy(world);
        WorldClockState state = snapshot(world, false);
        SchedulerUtil.runAsync(() -> {
            clockStore.write(state);
            api.getNetworkEventBus().publish(toEvent(state));
        });
    }

    private void pullAndApply() {
        if (authority.isAuthority()) return;
        SchedulerUtil.runAsync(() -> clockStore.read().ifPresent(state ->
                SchedulerUtil.run(() -> applyClock(state))));
    }

    private void applyClock(WorldClockState state) {
        World world = overworld.get();
        if (world == null) return;

        applyingClock.set(true);
        try {
            if (api.getServerRole() == ServerRole.SPAWN) {
                spawnFrozenTime.set(Math.floorMod(state.fullTime, 24000L));
                applySpawnFreeze(world);
            } else {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setFullTime(state.fullTime);
            }

            if (state.syncWeather || config.syncWeather()) {
                applyWeather(state);
            }
        } finally {
            applyingClock.set(false);
        }
    }

    private void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        handleCommand(event.getMessage());
    }

    private void onServerCommand(ServerCommandEvent event) {
        if (event.isCancelled()) return;
        handleCommand(event.getCommand());
    }

    private void handleCommand(String command) {
        String base = baseCommand(command);
        if (!base.equals("weather")) return;

        SchedulerUtil.runLater(() -> {
            World world = overworld.get();
            if (world == null) return;

            if (config.syncManualWeatherCommands()) {
                publish(snapshot(world, true));
            }
        }, 1L);
    }

    private void onTimeSkip(TimeSkipEvent event) {
        if (event.isCancelled() || applyingClock.get()) return;
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.COMMAND) return;

        SchedulerUtil.runLater(() -> {
            World world = event.getWorld();
            if (api.getServerRole() == ServerRole.SPAWN) {
                spawnFrozenTime.set(world.getTime());
                applySpawnFreeze(world);
            }
            publish(snapshot(world, false));
        }, 1L);
    }

    private void onWeatherChange(WeatherChangeEvent event) {
        if (event.isCancelled() || applyingClock.get()) return;
        if (!config.syncWeather() && (!config.syncManualWeatherCommands()
                || event.getCause() != WeatherChangeEvent.Cause.COMMAND)) return;
        publishWeatherSoon(event.getWorld());
    }

    private void onThunderChange(ThunderChangeEvent event) {
        if (event.isCancelled() || applyingClock.get()) return;
        if (!config.syncWeather() && (!config.syncManualWeatherCommands()
                || event.getCause() != ThunderChangeEvent.Cause.COMMAND)) return;
        publishWeatherSoon(event.getWorld());
    }

    private void publishWeatherSoon(World world) {
        SchedulerUtil.runLater(() -> publish(snapshot(world, true)), 1L);
    }

    private void publish(WorldClockState state) {
        SchedulerUtil.runAsync(() -> {
            clockStore.write(state);
            api.getNetworkEventBus().publish(toEvent(state));
        });
    }

    private void applyAuthorityPolicy(World world) {
        if (api.getServerRole() == ServerRole.SPAWN) {
            applySpawnFreeze(world);
            return;
        }
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
    }

    private void applyLocalPolicy(World world) {
        if (api.getServerRole() == ServerRole.SPAWN) {
            applySpawnFreeze(world);
        }
    }

    private void applySpawnFreeze(World world) {
        if (!config.spawnFreezeTime()) return;
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        if (config.spawnFreezeWeatherCycle()) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        }
        world.setTime(spawnFrozenTime.get());
    }

    private void applyWeather(WorldClockState state) {
        for (World world : Bukkit.getWorlds()) {
            world.setStorm(state.storm);
            world.setThundering(state.thunder);
            world.setWeatherDuration(state.weatherDuration);
            world.setThunderDuration(state.thunderDuration);
        }
    }

    private WorldClockState snapshot(World world, boolean syncWeather) {
        return new WorldClockState(
                world.getFullTime(),
                world.hasStorm(),
                world.isThundering(),
                world.getWeatherDuration(),
                world.getThunderDuration(),
                syncWeather
        );
    }

    private void resolveOverworld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                overworld.set(world);
                SchedulerUtil.run(() -> applyLocalPolicy(world));
                return;
            }
        }
    }

    private WorldClockState fromEvent(TimeWeatherSyncEvent event) {
        return new WorldClockState(event.fullTime, event.storm, event.thunder,
                event.weatherDuration, event.thunderDuration, event.syncWeather);
    }

    private TimeWeatherSyncEvent toEvent(WorldClockState state) {
        return new TimeWeatherSyncEvent(state.fullTime, state.storm, state.thunder,
                state.weatherDuration, state.thunderDuration, state.syncWeather, scope);
    }

    private String scope() {
        return api.getServerRole() == ServerRole.SPAWN ? "spawn" : "survival";
    }

    private String baseCommand(String message) {
        String command = message.startsWith("/") ? message.substring(1) : message;
        String base = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int namespace = base.indexOf(':');
        return namespace >= 0 ? base.substring(namespace + 1) : base;
    }
}
