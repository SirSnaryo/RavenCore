package com.kammoun.raven.velocity.info.config;

/** Keys of the multi-line message lists in {@code info/config.yml}. */
public enum InfoMessage {

    DISCORD("discord"),
    STORE("store"),
    RULES("rules"),
    MEDIA("media"),
    HELP("help");

    private final String path;

    InfoMessage(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
