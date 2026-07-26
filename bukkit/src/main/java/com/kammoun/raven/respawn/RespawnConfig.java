package com.kammoun.raven.respawn;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record RespawnConfig(List<String> clusters, DefaultSpawn defaultSpawn,
                            long resolutionTimeoutTicks, String noLocationMessage,
                            String transferFailedMessage) {
    public static RespawnConfig load(FileConfiguration config) {
        List<String> clusters = config.getStringList("clusters").stream()
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        String world = config.getString("default-spawn.world", "").trim();
        if (world.isEmpty()) {
            throw new IllegalArgumentException("respawn/config.yml default-spawn.world must be set");
        }
        DefaultSpawn defaultSpawn = new DefaultSpawn(world,
                finite(config.getDouble("default-spawn.x"), "x"),
                finite(config.getDouble("default-spawn.y"), "y"),
                finite(config.getDouble("default-spawn.z"), "z"),
                (float) finite(config.getDouble("default-spawn.yaw"), "yaw"),
                (float) finite(config.getDouble("default-spawn.pitch"), "pitch"));
        long timeoutTicks = Math.max(20L,
                Math.min(20L * 30L, config.getLong("resolution-timeout-seconds", 5L) * 20L));
        return new RespawnConfig(clusters, defaultSpawn, timeoutTicks,
                config.getString("messages.no-location", "&cNo safe respawn location is available."),
                config.getString("messages.transfer-failed", "&cCould not send you to your respawn server."));
    }

    private static double finite(double value, String coordinate) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("respawn/config.yml default-spawn." + coordinate
                    + " must be a finite number");
        }
        return value;
    }

    public record DefaultSpawn(String world, double x, double y, double z, float yaw, float pitch) {
    }
}
