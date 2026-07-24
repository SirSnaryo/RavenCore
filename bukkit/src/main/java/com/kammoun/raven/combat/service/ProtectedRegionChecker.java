package com.kammoun.raven.combat.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class ProtectedRegionChecker {
    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    public boolean inside(Location location) {
        if (!available() || location == null || location.getWorld() == null) return false;
        try {
            RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(location.getWorld()));
            return manager != null && !manager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(location)).getRegions().isEmpty();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
