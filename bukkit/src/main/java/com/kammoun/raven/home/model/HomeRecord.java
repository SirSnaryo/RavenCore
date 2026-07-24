package com.kammoun.raven.home.model;

import org.bukkit.Location;

import java.util.UUID;

public record HomeRecord(UUID playerUuid, String name, String serverId, String world,
                         double x, double y, double z, float yaw, float pitch) {
    public static HomeRecord from(UUID playerUuid, String name, String serverId, Location location) {
        return new HomeRecord(playerUuid, name, serverId, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
}
