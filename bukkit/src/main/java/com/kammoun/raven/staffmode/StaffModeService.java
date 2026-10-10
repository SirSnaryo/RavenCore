package com.kammoun.raven.staffmode;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffModeService {

    private static final String VANISH_PERMISSION = "raven.vanish";
    private static final String OWNER_VANISH_PERMISSION = "raven.ownervanish";

    private final Set<UUID> sessions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, VanishTier> vanish = new ConcurrentHashMap<>();
    private final Map<UUID, Flight> flight = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;
    private final StaffModeBars bars;
    private final StaffStateStore stateStore;
    private final FileConfiguration config;
    private final KSound toggleSound;
    private final KSound denySound;

    private record Flight(boolean allowFlight, boolean flying, GameMode gameMode) {
    }

    public StaffModeService(JavaPlugin plugin, FileConfiguration config, StaffModeBars bars,
                            StaffStateStore stateStore) {
        this.plugin = plugin;
        this.bars = bars;
        this.stateStore = stateStore;
        this.config = config;
        this.toggleSound = sound("sounds.toggle");
        this.denySound = sound("sounds.deny");
    }

    public boolean isEnabled(Player player) {
        return sessions.contains(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return vanish.containsKey(uuid);
    }

    public VanishTier vanishTier(UUID uuid) {
        return vanish.get(uuid);
    }

    public void toggleStaffMode(Player player) {
        if (isEnabled(player)) disableStaffMode(player);
        else enableStaffMode(player);
    }

    public void enableStaffMode(Player player) {
        if (!sessions.add(player.getUniqueId())) return;

        flight.put(player.getUniqueId(), new Flight(player.getAllowFlight(), player.isFlying(),
                player.getGameMode()));
        player.setAllowFlight(true);
        player.setFlying(true);

        play(player);
        bars.showStaff(player);
        persist(player);
    }

    public void disableStaffMode(Player player) {
        if (!sessions.remove(player.getUniqueId())) return;

        restoreFlight(player);
        play(player);
        bars.hideStaff(player);
        persist(player);
    }

    /** One command, two tiers: the highest tier the player holds decides which vanish they get. */
    public VanishTier tierFor(Player player) {
        return player.hasPermission(OWNER_VANISH_PERMISSION) ? VanishTier.OWNER_VANISH : VanishTier.VANISH;
    }

    public void toggleVanish(Player player) {
        toggleVanish(player, tierFor(player));
    }

    public void toggleVanish(Player player, VanishTier tier) {
        if (vanish.get(player.getUniqueId()) == tier) {
            vanish.remove(player.getUniqueId());
            bars.hideVanish(player);
            syncVisibility();
            play(player);
            persist(player);
            return;
        }

        vanish.put(player.getUniqueId(), tier);
        bars.showVanish(player, tier);
        syncVisibility();
        play(player);
        persist(player);
    }

    public boolean canSee(Player viewer, UUID target) {
        return canSee(vanish.get(target), viewer.getUniqueId().equals(target),
                viewer.hasPermission(OWNER_VANISH_PERMISSION), viewer.hasPermission(VANISH_PERMISSION));
    }

    static boolean canSee(VanishTier targetTier, boolean self, boolean viewerOwnerVanish,
                          boolean viewerVanish) {
        if (targetTier == null) return true;
        if (self) return true;
        if (viewerOwnerVanish) return true;
        return targetTier == VanishTier.VANISH && viewerVanish;
    }

    public void syncVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                SchedulerUtil.execute(viewer, () -> {
                    if (!viewer.isOnline() || !target.isOnline()) return;
                    if (canSee(viewer, target.getUniqueId())) viewer.showPlayer(plugin, target);
                    else viewer.hidePlayer(plugin, target);
                });
            }
        }
    }

    /**
     * Re-applies the state carried over from the previous server and re-runs the hide rules, so a
     * transferred player arrives already vanished with the right bars instead of flickering in.
     */
    public void restoreOnJoin(Player player) {
        if (stateStore == null) return;
        UUID uuid = player.getUniqueId();
        SchedulerUtil.runAsync(() -> {
            StaffStateStore.State state = stateStore.load(uuid);
            if (state == null) return;
            SchedulerUtil.execute(player, () -> {
                if (!player.isOnline()) return;
                if (state.staffMode()) {
                    sessions.add(uuid);
                    flight.put(uuid, new Flight(false, false, player.getGameMode()));
                    player.setAllowFlight(true);
                    player.setFlying(true);
                    bars.showStaff(player);
                }
                if (state.vanishTier() != null) {
                    vanish.put(uuid, state.vanishTier());
                    bars.showVanish(player, state.vanishTier());
                }
                syncVisibility();
            });
        });
    }

    public void forget(Player player) {
        UUID uuid = player.getUniqueId();
        sessions.remove(uuid);
        vanish.remove(uuid);
        restoreFlight(player);
        bars.hideAll(player);
        if (stateStore != null) stateStore.clear(uuid);
    }

    public void clear() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreFlight(player);
            if (stateStore != null) stateStore.clear(player.getUniqueId());
        }
        sessions.clear();
        vanish.clear();
        flight.clear();
        bars.clear();
    }

    private void restoreFlight(Player player) {
        Flight previous = flight.remove(player.getUniqueId());
        if (previous == null || !player.isOnline()) return;
        player.setAllowFlight(previous.allowFlight() || previous.gameMode() == GameMode.CREATIVE
                || previous.gameMode() == GameMode.SPECTATOR);
        player.setFlying(previous.flying());
    }

    private void persist(Player player) {
        if (stateStore == null) return;
        UUID uuid = player.getUniqueId();
        boolean staffMode = sessions.contains(uuid);
        VanishTier tier = vanish.get(uuid);
        SchedulerUtil.runAsync(() -> stateStore.save(uuid, staffMode, tier));
    }

    private void play(Player player) {
        if (toggleSound != null) toggleSound.playSound(player);
    }

    public void deny(Player player) {
        player.sendMessage(ChatFormatter.color(message("messages.enable-first",
                "&#D90429Enable Staff Mode first.")));
        if (denySound != null) denySound.playSound(player);
    }

    private String message(String path, String fallback) {
        return config == null ? fallback : config.getString(path, fallback);
    }

    private KSound sound(String path) {
        if (config == null) return null;
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? null : KSound.getSoundFromConfig(section);
    }
}
