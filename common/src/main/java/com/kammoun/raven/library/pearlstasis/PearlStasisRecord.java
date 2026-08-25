package com.kammoun.raven.library.pearlstasis;

import java.util.UUID;

public record PearlStasisRecord(
        long id,
        UUID playerUuid,
        String targetServer,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long createdAt
) {
    public PearlStasisRecord(UUID playerUuid, String targetServer, String world,
                             double x, double y, double z, float yaw, float pitch, long createdAt) {
        this(0L, playerUuid, targetServer, world, x, y, z, yaw, pitch, createdAt);
    }
}
