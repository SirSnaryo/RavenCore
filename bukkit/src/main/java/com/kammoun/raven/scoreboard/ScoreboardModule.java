package com.kammoun.raven.scoreboard;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.redis.event.BalanceUpdateEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.scoreboard.command.ScoreboardCommand;
import com.kammoun.raven.scoreboard.config.ScoreboardConfig;
import com.kammoun.raven.library.module.KModule;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ScoreboardModule extends KModule<RavenCore> {

    @Getter
    private ScoreboardService service;
    private SchedulerUtil.Task updateTask;
    private SchedulerUtil.Task conditionTask;
    private final Set<String> registeredBukkitEvents = new HashSet<>();

    public ScoreboardModule() {
        super("scoreboard");
    }

    @Override
    public void onModuleEnable() {
        saveDefaultConfig("config.example.yml");
        service = new ScoreboardService(api, new ScoreboardConfig(getConfig()));
        registerCommand(new ScoreboardCommand(this), "scoreboard");
        registerListeners();
        startTasks();

        if (getConfig().getBoolean("enabled", true)) {
            SchedulerUtil.runLater(() -> Bukkit.getOnlinePlayers().forEach(service::showIfAllowed), 10L);
        }

        getLogger().info("[Scoreboard] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        stopTasks();
        if (service != null) {
            service.clear();
        }
        api.getNetworkEventBus().unsubscribe(BalanceUpdateEvent.class);
        getLogger().info("[Scoreboard] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        stopTasks();
        service.reload(new ScoreboardConfig(getConfig()));
        startTasks();
        registerConfiguredBukkitEvents();
    }

    private void registerListeners() {
        onEvent(PlayerJoinEvent.class, event ->
                SchedulerUtil.runLater(() -> service.showIfAllowed(event.getPlayer()), 10L));
        onEvent(PlayerQuitEvent.class, event -> service.remove(event.getPlayer()));
        onEvent(PlayerChangedWorldEvent.class, event -> service.showIfAllowed(event.getPlayer()));

        api.getNetworkEventBus().subscribe(BalanceUpdateEvent.class, event -> {
            if (event.getUuid() == null) return;
            service.updateEvent(UUID.fromString(event.getUuid()), "balance");
        });

        registerConfiguredBukkitEvents();
    }

    private void startTasks() {
        ScoreboardConfig config = new ScoreboardConfig(getConfig());
        if (!config.isEnabled()) return;

        updateTask = SchedulerUtil.runTimer(
                () -> service.tick(config.getUpdateIntervalTicks()),
                config.getUpdateIntervalTicks(),
                config.getUpdateIntervalTicks()
        );
        conditionTask = SchedulerUtil.runTimer(
                service::refreshSelections,
                config.getConditionCheckTicks(),
                config.getConditionCheckTicks()
        );
    }

    private void stopTasks() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (conditionTask != null) {
            conditionTask.cancel();
            conditionTask = null;
        }
    }

    private void registerConfiguredBukkitEvents() {
        Set<String> eventNames = service.eventNames();
        for (String eventName : eventNames) {
            if (!eventName.contains(".")) continue;
            registerBukkitEvent(eventName);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerBukkitEvent(String className) {
        if (!registeredBukkitEvents.add(className)) {
            return;
        }

        try {
            Class<?> rawClass = Class.forName(className);
            if (!Event.class.isAssignableFrom(rawClass)) {
                getLogger().warning("[Scoreboard] Event is not a Bukkit event: " + className);
                return;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    new Listener() {},
                    EventPriority.MONITOR,
                    (listener, event) -> handleConfiguredEvent(event, className),
                    api
            );
        } catch (ClassNotFoundException e) {
            getLogger().warning("[Scoreboard] Unknown event class in config: " + className);
        }
    }

    private void handleConfiguredEvent(Event event, String eventName) {
        Player player = playerFrom(event);
        if (player != null) {
            service.updateEvent(player, eventName);
            return;
        }

        Bukkit.getOnlinePlayers().forEach(playerOnline -> service.updateEvent(playerOnline, eventName));
    }

    private Player playerFrom(Event event) {
        try {
            Method method = event.getClass().getMethod("getPlayer");
            Object value = method.invoke(event);
            return value instanceof Player player ? player : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
