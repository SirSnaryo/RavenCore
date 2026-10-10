package com.kammoun.raven.spawn.afk;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class AfkRegionChecker {

    private final List<String> regionNames;

    public AfkRegionChecker(List<String> regionNames) {
        this.regionNames = regionNames == null ? List.of() : List.copyOf(regionNames);
    }

    public static boolean worldGuardAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public boolean active() {
        return !regionNames.isEmpty() && worldGuardAvailable();
    }

    public boolean contains(Player player) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(player.getWorld()));
        if (manager == null) return false;

        ApplicableRegionSet regions = manager.getApplicableRegions(
                BukkitAdapter.adapt(player.getLocation()).toVector().toBlockPoint());
        for (ProtectedRegion region : regions) {
            for (String name : regionNames) {
                if (name.equalsIgnoreCase(region.getId())) return true;
            }
        }
        return false;
    }
}
