package com.kammoun.raven.afkportal;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import java.io.File;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import com.kammoun.raven.spawn.afk.AfkRegionChecker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkPortalModule extends KModule<RavenCore> {

    private static final long COOLDOWN_MILLIS = 2000L;

    private AfkRegionChecker regionChecker;
    private YamlConfiguration portalConfig;
    private boolean missingAfkWarning;
    private SchedulerUtil.Task checkTask;
    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();

    public AfkPortalModule() {
        super("afkportal");
    }

    @Override
    public void onModuleEnable() {
        saveDefaultConfig("afkportal.yml");
        portalConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "afkportal.yml"));

        if (!portalConfig.getBoolean("enabled", true)) {
            return;
        }
        if (!AfkRegionChecker.worldGuardAvailable()) {
            getLogger().warning("[AfkPortal] WorldGuard not found - AFK portal disabled.");
            return;
        }

        regionChecker = new AfkRegionChecker(portalConfig.getStringList("regions"));
        if (!regionChecker.active()) {
            getLogger().warning("[AfkPortal] No regions configured in afkportal.yml.");
            return;
        }

        checkTask = SchedulerUtil.runTimer(this::checkPlayers, 0L, 10L);
        getLogger().info("[AfkPortal] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        lastTeleport.clear();
    }

    private void checkPlayers() {
        Location afk = api.getSpawnModule() == null ? null : api.getSpawnModule().afkLocation();
        if (afk == null) {
            if (!missingAfkWarning) {
                missingAfkWarning = true;
                getLogger().warning("[AfkPortal] No stored /setafk location exists for this"
                        + " server - run /setafk so the portal has a destination.");
            }
            return;
        }

        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!regionChecker.contains(player)) continue;

            UUID uuid = player.getUniqueId();
            Long previous = lastTeleport.get(uuid);
            if (previous != null && now - previous < COOLDOWN_MILLIS) continue;

            Location target = afk.clone();
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());
            SchedulerUtil.execute(player, () -> player.teleportAsync(target));
            lastTeleport.put(uuid, now);
        }
    }
}
