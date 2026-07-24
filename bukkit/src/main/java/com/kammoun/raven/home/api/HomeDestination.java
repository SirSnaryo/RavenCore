package com.kammoun.raven.home.api;

import org.bukkit.Location;

import java.util.Objects;

/**
 * A server-aware destination accepted by the Home module.
 * Other modules, such as a future Team module, retain ownership of their
 * persistence and pass the stored destination here when they want to teleport.
 */
public record HomeDestination(String displayName, String serverId, String world,
                              double x, double y, double z, float yaw, float pitch) {
    public HomeDestination {
        displayName = require(displayName, "displayName");
        serverId = require(serverId, "serverId");
        world = require(world, "world");
    }

    public static HomeDestination from(String displayName, String serverId, Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) throw new IllegalArgumentException("location must have a world");
        return new HomeDestination(displayName, serverId, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value;
    }
}
