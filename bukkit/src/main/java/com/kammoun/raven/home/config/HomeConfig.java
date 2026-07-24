package com.kammoun.raven.home.config;

import org.bukkit.configuration.file.FileConfiguration;

public record HomeConfig(int defaultLimit, int warmupSeconds, boolean cancelOnMove,
                         boolean cancelOnDamage) {
    public static HomeConfig load(FileConfiguration config) {
        return new HomeConfig(Math.max(1, config.getInt("default-home-limit", 1)),
                Math.max(0, config.getInt("warmup-seconds", 3)),
                config.getBoolean("cancel-on-move", true),
                config.getBoolean("cancel-on-damage", true));
    }
}
