package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.rtp.config.RtpConfig;
import com.kammoun.raven.rtp.config.RtpMessages;
import com.kammoun.raven.rtp.menu.RtpClusterMenu;
import com.kammoun.raven.rtp.menu.RtpDimensionMenu;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Getter
public final class RtpModule extends KModule<RavenCore> implements Listener {
    private RtpConfig rtpConfig;
    private RtpMessages messages;
    private RtpSounds sounds;
    private RtpWarmupManager warmupManager;
    private RtpTeleportService teleportService;

    public RtpModule() {
        super("rtp");
    }

    @Override
    public void onModuleEnable() {
        loadState();
        registerCommand(new RtpCommand(this), "rtp");
        registerEvent(this);
    }

    @Override
    public void onModuleDisable() {
        if (warmupManager != null) warmupManager.stop();
        if (teleportService != null) teleportService.stop();
    }

    @Override
    public void reload() {
        onModuleDisable();
        reloadConfig();
        loadState();
    }

    public void openDimension(Player player) {
        new RtpDimensionMenu(api, this).open(player);
    }

    public void openClusters(Player player) {
        new RtpClusterMenu(api, this).open(player);
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
    }

    private void loadState() {
        ensureConfigDefaults();
        rtpConfig = RtpConfig.load(getConfig());
        messages = new RtpMessages(api);
        sounds = new RtpSounds(api);
        warmupManager = new RtpWarmupManager(api, rtpConfig, messages, sounds);
        RtpCooldownStore cooldowns = new RtpCooldownStore();
        teleportService = new RtpTeleportService(api, api.getLocationPoolModule().getPool(),
                rtpConfig, messages, sounds, cooldowns, warmupManager);
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
        if (changed) saveConfig();
    }
}
