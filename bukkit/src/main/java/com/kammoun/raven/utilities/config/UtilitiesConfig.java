package com.kammoun.raven.utilities.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class UtilitiesConfig {
    private final boolean hideJoin;
    private final boolean hideQuit;
    private final boolean keepFirstJoin;

    public UtilitiesConfig(FileConfiguration config) {
        hideJoin = config.getBoolean("join-messages.hide-join", true);
        hideQuit = config.getBoolean("join-messages.hide-quit", true);
        keepFirstJoin = config.getBoolean("join-messages.keep-first-join", false);
    }

    public boolean hideJoin() { return hideJoin; }
    public boolean hideQuit() { return hideQuit; }
    public boolean keepFirstJoin() { return keepFirstJoin; }
}
