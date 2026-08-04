package com.kammoun.raven.globalcontrol.listener;

import com.kammoun.raven.globalcontrol.GlobalMessageRenderer;
import com.kammoun.raven.globalcontrol.GlobalModule;
import com.kammoun.raven.globalcontrol.config.GlobalConfig;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GlobalListener implements Listener {

    private final GlobalModule module;
    private final GlobalMessageRenderer renderer = new GlobalMessageRenderer();

    public GlobalListener(GlobalModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        refreshCreativeFlight(event.getPlayer());
        GlobalConfig config = module.settings();
        if (!config.enabled() || !config.suppression().joinMessage()) return;
        event.joinMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled() || !config.suppression().quitMessage()) return;
        event.quitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled() || !config.suppression().advancementAnnouncements()) return;
        event.message(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled()) return;

        if (config.suppression().vanillaDeathMessages()) {
            event.deathMessage(null);
            event.setShowDeathMessages(false);
        }

        if (!config.deathMessages().enabled()) return;
        sendCustomDeathMessage(event, config.deathMessages());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled()) return;

        GlobalConfig.BossSounds bossSounds = config.bossSounds();
        EntityType entityType = event.getEntity().getType();
        if (!bossSounds.restrictGlobalDeathSounds() || !bossSounds.tracks(entityType)) return;

        Sound sound = event.getDeathSound() == null ? defaultBossDeathSound(entityType) : event.getDeathSound();
        SoundCategory category = event.getDeathSoundCategory() == null ? SoundCategory.HOSTILE : event.getDeathSoundCategory();
        float pitch = event.getDeathSoundPitch();
        float volume = event.getDeathSoundVolume() * bossSounds.volumeScale();
        Location location = event.getEntity().getLocation().clone();

        event.setShouldPlayDeathSound(false);

        if (!bossSounds.replayLocalSound() || sound == null) return;
        playNearby(location, bossSounds.radiusSquared(), sound, category, volume, pitch);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled() || !config.sleepActionBar().clear()) return;
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        module.requestSleepActionBarClear(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled() || !config.sleepActionBar().clear()) return;
        module.requestSleepActionBarClear(event.getPlayer().getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        GlobalConfig config = module.settings();
        if (!config.enabled() || !config.gameRules().applyOnWorldLoad()) return;
        module.applyGameRules(event.getWorld());
    }

    private void sendCustomDeathMessage(PlayerDeathEvent event, GlobalConfig.DeathMessages config) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        String cause = victim.getLastDamageCause() == null
                ? "unknown"
                : victim.getLastDamageCause().getCause().name().toLowerCase(Locale.ROOT);
        String template = killer == null ? config.genericFormat() : config.playerKillFormat();
        Component message = renderer.render(template, Map.of(
                "%victim%", victim.getName(),
                "%killer%", killer == null ? "" : killer.getName(),
                "%cause%", cause
        ));
        sendNearby(victim.getLocation().clone(), config.radiusSquared(), player -> player.sendMessage(message));
    }

    private void playNearby(Location origin, double radiusSquared, Sound sound,
                            SoundCategory category, float volume, float pitch) {
        sendNearby(origin, radiusSquared, player -> player.playSound(origin, sound, category, volume, pitch));
    }

    private Sound defaultBossDeathSound(EntityType type) {
        return switch (type) {
            case ENDER_DRAGON -> Sound.ENTITY_ENDER_DRAGON_DEATH;
            case WITHER -> Sound.ENTITY_WITHER_DEATH;
            default -> null;
        };
    }

    private void refreshCreativeFlight(Player player) {
        SchedulerUtil.runLater(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) return;
            GameMode mode = player.getGameMode();
            if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) return;

            boolean wasFlying = player.isFlying();
            player.setAllowFlight(false);
            player.setAllowFlight(true);
            if (wasFlying || mode == GameMode.SPECTATOR) {
                player.setFlying(true);
            }
        }), 2L);
    }

    private void sendNearby(Location origin, double radiusSquared, PlayerConsumer consumer) {
        World world = origin.getWorld();
        if (world == null) return;
        UUID worldId = world.getUID();

        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.execute(player, () -> {
                if (!player.isOnline()) return;
                if (!player.getWorld().getUID().equals(worldId)) return;
                if (player.getLocation().distanceSquared(origin) > radiusSquared) return;
                consumer.accept(player);
            });
        }
    }

    @FunctionalInterface
    private interface PlayerConsumer {
        void accept(Player player);
    }
}
