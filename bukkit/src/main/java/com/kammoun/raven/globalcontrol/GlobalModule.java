package com.kammoun.raven.globalcontrol;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.globalcontrol.config.GlobalConfig;
import com.kammoun.raven.globalcontrol.listener.GlobalListener;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalModule extends KModule<RavenCore> {

    private GlobalConfig settings;
    private final Map<UUID, Long> sleepActionBarClearUntil = new ConcurrentHashMap<>();
    private SchedulerUtil.Task sleepActionBarTask;

    public GlobalModule() {
        super("global");
    }

    @Override
    public void onModuleEnable() {
        settings = GlobalConfig.load(getConfig(), getLogger());
        registerEvent(new GlobalListener(this));
        applyGameRules();
        getLogger().info("[Global] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        stopSleepActionBarTask();
        getLogger().info("[Global] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        settings = GlobalConfig.load(getConfig(), getLogger());
        stopSleepActionBarTask();
        applyGameRules();
        getLogger().info("[Global] Configuration reloaded.");
    }

    public GlobalConfig settings() {
        return settings;
    }

    public void requestSleepActionBarClear(World world) {
        if (!settings.enabled() || !settings.sleepActionBar().clear()) return;

        long until = System.currentTimeMillis() + (settings.sleepActionBar().durationTicks() * 50L);
        sleepActionBarClearUntil.merge(world.getUID(), until, Math::max);
        startSleepActionBarTask();
    }

    private void applyGameRules() {
        if (!settings.enabled() || !settings.gameRules().applyOnEnable()) return;
        for (World world : Bukkit.getWorlds()) {
            applyGameRules(world);
        }
    }

    @SuppressWarnings("removal")
    public void applyGameRules(World world) {
        if (!settings.enabled()) return;

        GlobalConfig.GameRules gameRules = settings.gameRules();
        if (settings.suppression().advancementAnnouncements()) {
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, gameRules.announceAdvancements());
        }
        if (settings.suppression().vanillaDeathMessages()) {
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, gameRules.showDeathMessages());
        }
    }

    private void startSleepActionBarTask() {
        if (sleepActionBarTask != null) return;

        int period = settings.sleepActionBar().everyTicks();
        sleepActionBarTask = SchedulerUtil.runTimer(this::clearSleepActionBars, 1L, period);
    }

    private void clearSleepActionBars() {
        long now = System.currentTimeMillis();
        sleepActionBarClearUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (sleepActionBarClearUntil.isEmpty()) {
            stopSleepActionBarTask();
            return;
        }

        Set<UUID> activeWorlds = Set.copyOf(sleepActionBarClearUntil.keySet());
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.execute(player, () -> {
                if (player.isOnline() && activeWorlds.contains(player.getWorld().getUID())) {
                    player.sendActionBar(Component.empty());
                }
            });
        }
    }

    private void stopSleepActionBarTask() {
        sleepActionBarClearUntil.clear();
        if (sleepActionBarTask != null) {
            sleepActionBarTask.cancel();
            sleepActionBarTask = null;
        }
    }
}
