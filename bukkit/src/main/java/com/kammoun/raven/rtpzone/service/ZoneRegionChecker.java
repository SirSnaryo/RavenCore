package com.kammoun.raven.rtpzone.service;

import com.kammoun.raven.rtpzone.RtpZoneModule;
import com.kammoun.raven.rtpzone.manager.ZonePlayerManager;
import com.kammoun.raven.rtpzone.manager.ZoneSoundManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Polls every online player each second and syncs the {@link ZonePlayerManager} with who is
 * actually inside one of the configured WorldGuard regions, firing enter/exit cues on the edges.
 * The countdown itself is server-side and keeps running regardless of who is inside.
 */
public class ZoneRegionChecker {

    private final List<String> regionNames;

    public ZoneRegionChecker(List<String> regionNames) {
        this.regionNames = regionNames;
    }

    public void checkPlayers(RtpZoneModule module, ZonePlayerManager playerManager, ZoneSoundManager soundManager) {
        if (regionNames.isEmpty()) return;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        for (Player player : Bukkit.getOnlinePlayers()) {
            RegionManager rm = container.get(BukkitAdapter.adapt(player.getWorld()));
            if (rm == null) {
                if (playerManager.contains(player)) exitZone(player, playerManager, soundManager);
                continue;
            }

            ApplicableRegionSet regions = rm.getApplicableRegions(
                    BukkitAdapter.adapt(player.getLocation()).toVector().toBlockPoint());

            boolean inZone = false;
            for (ProtectedRegion region : regions) {
                if (regionNames.stream().anyMatch(n -> n.equalsIgnoreCase(region.getId()))) {
                    inZone = true;
                    if (!playerManager.contains(player)) {
                        playerManager.add(player);
                        soundManager.playEnter(player);
                    }
                    break;
                }
            }

            if (!inZone && playerManager.contains(player)) {
                exitZone(player, playerManager, soundManager);
            }
        }

        playerManager.removeOffline();
    }

    private void exitZone(Player player, ZonePlayerManager playerManager, ZoneSoundManager soundManager) {
        playerManager.remove(player);
        soundManager.playExit(player);
        player.resetTitle();
    }
}
