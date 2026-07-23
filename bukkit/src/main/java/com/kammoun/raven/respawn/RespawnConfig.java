package com.kammoun.raven.respawn;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record RespawnConfig(List<String> clusters, String noLocationMessage,
                            String transferFailedMessage) {
    public static RespawnConfig load(FileConfiguration config) {
        List<String> clusters = config.getStringList("clusters").stream()
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        return new RespawnConfig(clusters,
                config.getString("messages.no-location", "&cNo safe respawn location is available."),
                config.getString("messages.transfer-failed", "&cCould not send you to your respawn server."));
    }
}
