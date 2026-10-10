package com.kammoun.raven.devmode;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DevModeService {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final DevModeStateStore stateStore;
    private final Map<UUID, DevModeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final KSound toggleSound;
    private SchedulerUtil.Task task;

    public DevModeService(JavaPlugin plugin, FileConfiguration config, DevModeStateStore stateStore) {
        this.plugin = plugin;
        this.config = config;
        this.stateStore = stateStore;
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("sounds.toggle");
        this.toggleSound = section == null ? null : KSound.getSoundFromConfig(section);
    }

    public boolean toggle(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public void enable(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.putIfAbsent(uuid, new DevModeSession(uuid)) != null) return;
        startHudTask();
        updateHud(player, DevSnapshot.capture());
        play(player);
        persist(player, true);
    }

    public void disable(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.remove(uuid) == null) return;
        removeBossBar(uuid);
        player.sendActionBar(Component.empty());
        play(player);
        persist(player, false);
        stopHudTaskIfIdle();
    }

    /** Drops the local session without touching the carried state, so a transfer can restore it. */
    public void forget(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.remove(uuid) == null) return;
        removeBossBar(uuid);
        stopHudTaskIfIdle();
    }

    /** Re-applies Developer Mode carried over from the previous server. */
    public void restoreOnJoin(Player player) {
        if (stateStore == null || !stateStore.load(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        if (sessions.putIfAbsent(uuid, new DevModeSession(uuid)) != null) return;
        startHudTask();
        updateHud(player, DevSnapshot.capture());
    }

    public void clear() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID uuid : sessions.keySet()) {
            if (stateStore != null) stateStore.clear(uuid);
        }
        sessions.clear();
        if (plugin.isEnabled()) {
            bossBars.values().forEach(BossBar::removeAll);
        }
        bossBars.clear();
    }

    private void startHudTask() {
        if (task != null) return;
        long period = Math.max(5L, config.getLong("hud.update-ticks", 20L));
        task = SchedulerUtil.runTimer(() -> {
            if (sessions.isEmpty()) {
                stopHudTaskIfIdle();
                return;
            }
            // One sample per tick, shared by every viewer.
            DevSnapshot snapshot = DevSnapshot.capture();
            Set<UUID> watching = new HashSet<>(sessions.keySet());
            for (UUID uuid : watching) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                SchedulerUtil.execute(player, () -> {
                    if (player.isOnline() && sessions.containsKey(uuid)) {
                        updateHud(player, snapshot);
                    }
                });
            }
        }, period, period);
    }

    private void stopHudTaskIfIdle() {
        if (!sessions.isEmpty() || task == null) return;
        task.cancel();
        task = null;
    }

    private void updateHud(Player player, DevSnapshot snapshot) {
        String actionBar = config.getString("hud.actionbar",
                "&#FF2B2B&lDeveloper Mode &#7A7A7A| &#FFFFFFTPS %tps% &#7A7A7A| &#FFFFFFMSPT %mspt% "
                        + "&#7A7A7A| &#FFFFFFCPU %cpu% &#7A7A7A| &#FFFFFFHeap %heap%");
        player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize(ChatFormatter.color(apply(actionBar, snapshot))));

        if (!config.getBoolean("hud.bossbar.enabled", true)) return;
        UUID uuid = player.getUniqueId();
        BossBar bossBar = bossBars.get(uuid);
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("", snapshot.color(), BarStyle.SOLID);
            bossBar.addPlayer(player);
            bossBars.put(uuid, bossBar);
        }
        bossBar.setColor(snapshot.color());
        bossBar.setProgress(snapshot.progress());
        bossBar.setTitle(ChatFormatter.color(apply(config.getString("hud.bossbar.text",
                "&#FF2B2B&lDeveloper Mode &#7A7A7A| &#FFFFFFTPS %tps% &#7A7A7A| &#FFFFFFMSPT %mspt% "
                        + "&#7A7A7A| &#FFFFFFCPU %cpu%"), snapshot)));
    }

    private String apply(String text, DevSnapshot snapshot) {
        return text
                .replace("%tps%", snapshot.tps())
                .replace("%mspt%", snapshot.mspt())
                .replace("%heap%", snapshot.heap())
                .replace("%allocated%", snapshot.allocated())
                .replace("%cpu%", snapshot.cpu())
                .replace("%process_cpu%", snapshot.processCpu())
                .replace("%system_cpu%", snapshot.systemCpu())
                .replace("%chunks%", snapshot.chunks())
                .replace("%entities%", snapshot.entities())
                .replace("%online%", snapshot.online())
                .replace("%uptime%", snapshot.uptime())
                .replace("%ping%", snapshot.ping())
                .replace("%server%", snapshot.server());
    }

    private void persist(Player player, boolean active) {
        if (stateStore == null) return;
        UUID uuid = player.getUniqueId();
        SchedulerUtil.runAsync(() -> stateStore.save(uuid, active));
    }

    private void play(Player player) {
        if (toggleSound != null) toggleSound.playSound(player);
    }

    private void removeBossBar(UUID uuid) {
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) bossBar.removeAll();
    }
}
