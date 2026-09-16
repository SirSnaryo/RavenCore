package com.kammoun.raven.devmode;

import com.kammoun.raven.devmode.map.DevMapService;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DevModeService {

    private static final int[] MAP_SLOTS = {0, 1, 2};

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final DevMapService mapService;
    private final Map<UUID, DevModeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private SchedulerUtil.Task task;

    public DevModeService(JavaPlugin plugin, FileConfiguration config, DevMapService mapService) {
        this.plugin = plugin;
        this.config = config;
        this.mapService = mapService;
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
        if (sessions.containsKey(player.getUniqueId())) return;
        sessions.put(player.getUniqueId(), new DevModeSession(captureHotbar(player)));
        startHudTask();
        applyMaps(player);
        updateHud(player);
        player.sendMessage(ChatFormatter.color(config.getString("messages.enabled", "&#33FF66DevMode enabled.")));
    }

    public void disable(Player player) {
        DevModeSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        restoreHotbar(player, session.hotbar());
        removeBossBar(player.getUniqueId());
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
        player.sendMessage(ChatFormatter.color(config.getString("messages.disabled", "&#D90429DevMode disabled.")));
        stopHudTaskIfIdle();
    }

    public void clear() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (plugin.isEnabled()) {
            for (Map.Entry<UUID, DevModeSession> entry : sessions.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;
                SchedulerUtil.execute(player, () -> {
                    restoreHotbar(player, entry.getValue().hotbar());
                    player.sendActionBar(net.kyori.adventure.text.Component.empty());
                    removeBossBar(player.getUniqueId());
                });
            }
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
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!sessions.containsKey(player.getUniqueId())) continue;
                SchedulerUtil.execute(player, () -> {
                    if (player.isOnline() && sessions.containsKey(player.getUniqueId())) {
                        updateHud(player);
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

    private void updateHud(Player player) {
        String actionBar = replace(config.getString("hud.actionbar",
                "&#FF2B2BRaven Dev &#7A7A7A| &#FFFFFFTPS &#33FF66%tps% &#7A7A7A| &#FFFFFFMSPT &#FF9F1C%mspt% &#7A7A7A| &#FFFFFFHeap &#A855F7%heap%"));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ChatFormatter.color(actionBar)));

        if (!config.getBoolean("hud.bossbar.enabled", true)) return;
        BossBar bossBar = bossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar created = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
            created.addPlayer(player);
            return created;
        });
        bossBar.setTitle(ChatFormatter.color(replace(config.getString("hud.bossbar.text",
                "&#FF2B2BDevMode &#7A7A7A| &#FFFFFFCPU &#A855F7%cpu% &#7A7A7A| &#FFFFFFOnline &#4CC9F0%online%"))));
        bossBar.setProgress(Math.max(0.05D, Math.min(1D, 1D - (Bukkit.getAverageTickTime() / 100D))));
    }

    private String replace(String text) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long used = heap.getUsed() / 1024L / 1024L;
        long max = heap.getMax() / 1024L / 1024L;
        return text
                .replace("%tps%", String.format("%.2f", Bukkit.getTPS()[0]))
                .replace("%mspt%", String.format("%.1f", Bukkit.getAverageTickTime()))
                .replace("%heap%", used + "/" + max + "M")
                .replace("%cpu%", cpu())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%server%", Bukkit.getServer().getName());
    }

    private String cpu() {
        try {
            Object bean = ManagementFactory.getOperatingSystemMXBean();
            double load = (double) bean.getClass().getMethod("getProcessCpuLoad").invoke(bean);
            if (load < 0D) return "n/a";
            return String.format("%.0f%%", load * 100D);
        } catch (ReflectiveOperationException ignored) {
            return "n/a";
        }
    }

    private ItemStack[] captureHotbar(Player player) {
        ItemStack[] items = new ItemStack[MAP_SLOTS.length];
        for (int i = 0; i < MAP_SLOTS.length; i++) {
            ItemStack item = player.getInventory().getItem(MAP_SLOTS[i]);
            items[i] = item == null ? null : item.clone();
        }
        return items;
    }

    private void applyMaps(Player player) {
        if (!config.getBoolean("maps.enabled", false)) {
            return;
        }
        player.getInventory().setItem(0, mapService.createPerformanceMap(player));
        player.getInventory().setItem(1, mapService.createMemoryMap(player));
        player.getInventory().setItem(2, mapService.createNetworkMap(player));
    }

    private void restoreHotbar(Player player, ItemStack[] hotbar) {
        if (hotbar == null) return;
        for (int i = 0; i < MAP_SLOTS.length && i < hotbar.length; i++) {
            player.getInventory().setItem(MAP_SLOTS[i], hotbar[i] == null ? null : hotbar[i].clone());
        }
    }

    private void removeBossBar(UUID uuid) {
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) bossBar.removeAll();
    }
}
