package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.redis.event.RtpQueueNotificationEvent;
import com.kammoun.raven.rtp.config.RtpConfig;
import com.kammoun.raven.rtp.config.RtpMessages;
import com.kammoun.raven.rtp.menu.RtpClusterMenu;
import com.kammoun.raven.rtp.menu.RtpDimensionMenu;
import com.kammoun.raven.rtp.queue.RtpQueueService;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class RtpModule extends KModule<RavenCore> implements Listener {
    private RtpConfig rtpConfig;
    private RtpMessages messages;
    private RtpSounds sounds;
    private RtpWarmupManager warmupManager;
    private RtpTeleportService teleportService;
    private RtpQueueService queueService;

    public RtpModule() {
        super("rtp");
    }

    @Override
    public void onModuleEnable() {
        loadState();
        registerCommand(new RtpCommand(this), "rtp");
        registerCommand(new RtpQueueCommand(this), "rtpq");
        registerEvent(this);
        api.getNetworkEventBus().subscribe(RtpQueueNotificationEvent.class, this::onQueueNotification);
    }

    @Override
    public void onModuleDisable() {
        if (warmupManager != null) warmupManager.stop();
        if (teleportService != null) teleportService.stop();
        if (queueService != null) queueService.stop();
        api.getNetworkEventBus().unsubscribe(RtpQueueNotificationEvent.class);
    }

    @Override
    public void reload() {
        onModuleDisable();
        reloadConfig();
        loadState();
        api.getNetworkEventBus().subscribe(RtpQueueNotificationEvent.class, this::onQueueNotification);
    }

    public void openDimension(Player player) {
        new RtpDimensionMenu(api, this).open(player);
    }

    public void openClusters(Player player) {
        openClusters(player, LocationDimension.OVERWORLD);
    }

    public void openClusters(Player player, LocationDimension dimension) {
        new RtpClusterMenu(api, this, dimension).open(player);
    }

    public RtpOnlineSnapshot onlineSnapshot() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        if (api.getNetworkPlayerManager() == null) {
            counts.put(api.getServerId(), (long) api.getServer().getOnlinePlayers().size());
            displayNames.put(api.getServerId(), api.getServerDisplayName());
            return new RtpOnlineSnapshot(counts, displayNames);
        }

        for (NetworkPlayerInfo info : api.getNetworkPlayerManager().getOnlineNetworkPlayers()) {
            if (info.getServerId() == null || info.getServerId().isBlank()) continue;
            counts.merge(info.getServerId(), 1L, Long::sum);
            if (info.getServerDisplayName() != null && !info.getServerDisplayName().isBlank()) {
                displayNames.putIfAbsent(info.getServerId(), info.getServerDisplayName());
            }
        }
        return new RtpOnlineSnapshot(counts, displayNames);
    }

    public List<String> registeredServers(String cluster, LocationDimension dimension) {
        if (api.getLocationPoolModule() == null || api.getLocationPoolModule().getLocationPool() == null) {
            return List.of();
        }
        return api.getLocationPoolModule().getLocationPool().getRegisteredServers(cluster, dimension);
    }

    public List<String> registeredServersAcrossClusters(LocationDimension dimension) {
        if (rtpConfig == null) {
            return List.of();
        }
        if (api.getLocationPoolModule() == null || api.getLocationPoolModule().getLocationPool() == null) {
            return List.of();
        }
        return api.getLocationPoolModule().getLocationPool()
                .getRegisteredServersAcrossClusters(rtpConfig.overworldClusters().keySet(), dimension);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        warmupManager.onMove(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        warmupManager.onDamage(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warmupManager.onQuit(event);
        if (queueService != null) queueService.remove(event.getPlayer());
    }

    private void onQueueNotification(RtpQueueNotificationEvent event) {
        if (queueService != null) queueService.deliver(event);
    }

    private void loadState() {
        ensureConfigDefaults();
        ensureGuiDefaults();
        rtpConfig = RtpConfig.load(getConfig());
        messages = new RtpMessages(api);
        sounds = new RtpSounds(api);
        warmupManager = new RtpWarmupManager(api, rtpConfig, messages, sounds);
        RtpCooldownStore cooldowns = new RtpCooldownStore();
        teleportService = new RtpTeleportService(api, api.getLocationPoolModule().getPool(),
                rtpConfig, messages, sounds, cooldowns, warmupManager);
        queueService = new RtpQueueService(api, this, rtpConfig, messages, teleportService);
        queueService.start();
    }

    private void ensureConfigDefaults() {
        boolean changed = false;
        if (!getConfig().contains("end-server")) {
            getConfig().set("end-server", "smp-end");
            changed = true;
        }
        if (!getConfig().contains("overworld-clusters.na.display-name")) {
            getConfig().set("overworld-clusters.na.display-name", "North America");
            changed = true;
        }
        if (!getConfig().contains("overworld-clusters.eu.display-name")) {
            getConfig().set("overworld-clusters.eu.display-name", "Europe");
            changed = true;
        }
        changed |= setMissing("queue.enabled", true);
        changed |= setMissing("queue.distance-blocks", 7.0D);
        changed |= setMissing("queue.entry-ttl-seconds", 300);
        changed |= setMissing("queue.lock-millis", 5000L);
        changed |= setMissing("queue.match-interval-ticks", 40L);
        if (changed) saveConfig();
    }

    private boolean setMissing(String path, Object value) {
        if (getConfig().contains(path)) {
            return false;
        }
        getConfig().set(path, value);
        return true;
    }

    private void ensureGuiDefaults() {
        ensureOnlineLore("gui/rtp.yml", "Items.Overworld.lore", "Items.Nether.lore", "Items.End.lore");
        ensureOnlineLore("gui/region-menu.yml", "Items.Region.lore");
        ensureOnlineLore("gui/nether-region-menu.yml", "Items.Region.lore");
    }

    private void ensureOnlineLore(String fileName, String... lorePaths) {
        saveDefaultConfig(fileName);
        File file = new File(moduleDataFolder, fileName);
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (String path : lorePaths) {
            List<String> lore = yaml.getStringList(path);
            if (lore.isEmpty()) {
                continue;
            }

            List<String> updated = new ArrayList<>(lore);
            boolean removedServerRows = updated.removeIf(line -> line.contains("%server_online_lines%"));
            boolean hasPlayers = updated.stream().anyMatch(line -> line.contains("%online%"));
            boolean renamed = false;
            for (int i = 0; i < updated.size(); i++) {
                String line = updated.get(i);
                if (line.contains("%online%") && line.toLowerCase(java.util.Locale.ROOT).contains("online")) {
                    updated.set(i, "&#33FF66Players: &#FFFFFF%online%");
                    renamed = true;
                }
            }
            if (!hasPlayers) {
                int insertAt = findClickLine(updated);
                if (insertAt > 0 && updated.get(insertAt - 1).isBlank()) {
                    insertAt--;
                }
                updated.add(insertAt, "&#33FF66Players: &#FFFFFF%online%");
                if (insertAt + 1 >= updated.size() || !updated.get(insertAt + 1).isBlank()) {
                    updated.add(insertAt + 1, "");
                }
            }
            if (removedServerRows || renamed || !hasPlayers) {
                yaml.set(path, updated);
                changed = true;
            }
        }

        if (!changed) return;
        try {
            yaml.save(file);
        } catch (IOException e) {
            getLogger().warning("[RTP] Failed to update GUI defaults in " + fileName + ": " + e.getMessage());
        }
    }

    private int findClickLine(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).toUpperCase().contains("CLICK")) {
                return i;
            }
        }
        return lore.size();
    }

    public record RtpOnlineSnapshot(Map<String, Long> onlineByServer, Map<String, String> displayNames) {
        public long online(Collection<String> serverIds) {
            if (serverIds == null || serverIds.isEmpty()) {
                return 0L;
            }
            return serverIds.stream()
                    .mapToLong(serverId -> onlineByServer.getOrDefault(serverId, 0L))
                    .sum();
        }

        public String displayName(String serverId) {
            return displayNames.getOrDefault(serverId, serverId);
        }
    }
}
