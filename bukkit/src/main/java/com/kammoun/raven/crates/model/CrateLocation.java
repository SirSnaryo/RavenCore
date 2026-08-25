package com.kammoun.raven.crates.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record CrateLocation(String world, int x, int y, int z, String crateId) {
    public static CrateLocation of(Location location, String crateId) {
        return new CrateLocation(location.getWorld().getName(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ(), crateId);
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
