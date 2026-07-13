package com.kammoun.raven.library.platform;

import org.bukkit.World;

public enum ServerRole {

    OVERWORLD(null),
    NETHER(World.Environment.NETHER),
    END(World.Environment.THE_END),
    SPAWN(null);

    private final World.Environment requiredEnvironment;

    ServerRole(World.Environment requiredEnvironment) {
        this.requiredEnvironment = requiredEnvironment;
    }

    /** Returns true if the given world should be kept on this server. */
    public boolean allows(World.Environment env) {
        return requiredEnvironment == null || env == requiredEnvironment;
    }

    public static ServerRole parse(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OVERWORLD;
        }
    }
}
