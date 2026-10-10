package com.kammoun.raven.rtpzone;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.platform.title.KTitle;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.rtpzone.config.RtpZoneConfig;
import com.kammoun.raven.rtpzone.manager.ZonePlayerManager;
import com.kammoun.raven.rtpzone.manager.ZoneSoundManager;
import com.kammoun.raven.rtpzone.manager.ZoneTeleportManager;
import com.kammoun.raven.rtpzone.papi.RtpZonePlaceholders;
import com.kammoun.raven.rtpzone.service.ZoneRegionChecker;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;

/**
 * Region-triggered RTP: players who stand inside a configured WorldGuard region start a shared
 * countdown, and when it hits zero the whole group is flung to a random overworld survival server.
 *
 * <p>The "random server + safe random overworld location" half is <em>not</em> reimplemented here —
 * it reuses the /rtp module's cluster list and the shared LocationPool, so a zone teleport lands on a real,
 * pre-validated safe spot. This module only owns the region detection, countdown and cues.</p>
 *
 * <p>Enabled on the SPAWN role only (where the zone physically lives). Requires WorldGuard; if it
 * isn't installed the module stays inert rather than erroring.</p>
 */
@Getter
public class RtpZoneModule extends KModule<RavenCore> {

    private RtpZoneConfig zoneConfig;
    private ZonePlayerManager playerManager;
    private ZoneSoundManager soundManager;
    private ZoneTeleportManager teleportManager;
    private ZoneRegionChecker regionChecker;

    private int countdownTime;
    private int currentTime;

    private SchedulerUtil.Task countdownTask;
    private SchedulerUtil.Task regionCheckTask;
    private boolean active;

    public RtpZoneModule() {
        super("rtpzone");
    }

    @Override
    public void onModuleEnable() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().warning("[RtpZone] WorldGuard not found — RTP zone disabled.");
            return;
        }

        this.zoneConfig    = RtpZoneConfig.load(getConfig());
        this.soundManager  = new ZoneSoundManager(getConfig());
        this.playerManager = new ZonePlayerManager();
        this.regionChecker = new ZoneRegionChecker(zoneConfig.getRegions());

        this.teleportManager = new ZoneTeleportManager(this,
                api.getLocationPoolModule().getPool(),
                api.getRtpModule().getRtpConfig().overworldClusters().keySet());

        this.countdownTime = zoneConfig.getTimer();
        this.currentTime   = countdownTime;

        if (KPlaceholderExpansion.isAvailable()) {
            new RtpZonePlaceholders(api, this).register();
        }

        registerEvent();
        startRegionCheckTask();
        startCountdown();
        this.active = true;

        getLogger().info("[RtpZone] Enabled (" + zoneConfig.getRegions().size() + " region(s)).");
    }

    @Override
    public void onModuleDisable() {
        stopCountdown();
        if (regionCheckTask != null) {
            regionCheckTask.cancel();
            regionCheckTask = null;
        }
        if (playerManager != null) playerManager.clearAll();
        active = false;
    }

    @Override
    public void reload() {
        if (!active) return;
        stopCountdown();
        if (regionCheckTask != null) {
            regionCheckTask.cancel();
            regionCheckTask = null;
        }
        reloadConfig();

        this.zoneConfig    = RtpZoneConfig.load(getConfig());
        this.soundManager  = new ZoneSoundManager(getConfig());
        this.regionChecker = new ZoneRegionChecker(zoneConfig.getRegions());

        this.teleportManager = new ZoneTeleportManager(this,
                api.getLocationPoolModule().getPool(),
                api.getRtpModule().getRtpConfig().overworldClusters().keySet());

        this.countdownTime = zoneConfig.getTimer();
        this.currentTime   = countdownTime;

        playerManager.clearAll();
        startRegionCheckTask();
        startCountdown();
    }

    // -------------------------------------------------------------------------
    // Countdown

    /**
     * The countdown is owned by the server, not by the players standing in the zone: it ticks
     * continuously from enable to disable and never restarts when someone enters or leaves, so
     * joining mid-cycle shows the time still remaining instead of a fresh countdown.
     */
    public void startCountdown() {
        if (countdownTask != null) return;

        countdownTask = SchedulerUtil.runTimer(() -> {
            if (currentTime <= 0) {
                currentTime = countdownTime;
                Set<Player> group = playerManager.getOnlinePlayers();
                if (group.isEmpty()) return;
                playerManager.clearAll();
                teleportManager.teleportAll(group);
                return;
            }

            broadcastCountdown();
            currentTime--;
        }, 0L, 20L);
    }

    public void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        this.currentTime = countdownTime;
    }

    private void broadcastCountdown() {
        for (Player player : playerManager.getOnlinePlayers()) {
            SchedulerUtil.execute(player, () -> {
                KTitle title = zoneConfig.getCountdownTitle().withPlaceholders(
                        "%time%", String.valueOf(currentTime));
                title.send(player);
                soundManager.playCountdown(player);
            });
        }
    }

    // -------------------------------------------------------------------------

    private void registerEvent() {
        onEvent(PlayerQuitEvent.class, event -> playerManager.remove(event.getPlayer()));
        // Re-check a joining player next tick so someone logging in inside the zone is picked up.
        onEvent(PlayerJoinEvent.class, event -> SchedulerUtil.runLater(() -> {
            if (event.getPlayer().isOnline()) {
                regionChecker.checkPlayers(this, playerManager, soundManager);
            }
        }, 1L));
    }

    private void startRegionCheckTask() {
        regionCheckTask = SchedulerUtil.runTimer(
                () -> regionChecker.checkPlayers(this, playerManager, soundManager), 0L, 20L);
    }

}
