package com.kammoun.raven.home.config;

import org.bukkit.configuration.file.FileConfiguration;

public record HomeConfig(int defaultLimit, int maxLimit, int warmupSeconds, boolean cancelOnMove,
                         boolean cancelOnDamage) {
    public static HomeConfig load(FileConfiguration config) {
        int maxLimit = Math.max(1, config.getInt("max-home-limit", 5));
        int defaultLimit = Math.max(1, config.getInt("default-home-limit", maxLimit));
        return new HomeConfig(Math.min(defaultLimit, maxLimit),
                maxLimit,
                Math.max(0, config.getInt("warmup-seconds", 3)),
                config.getBoolean("cancel-on-move", true),
                config.getBoolean("cancel-on-damage", true));
    }
}
