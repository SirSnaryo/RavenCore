package com.kammoun.raven.rtp.pool;

/** One pre-generated safe landing spot, serialized as-is into the Redis pool/transfer payloads. */
public final class PooledLocation {

    public final String world;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;

    public PooledLocation(String world, double x, double y, double z, float yaw) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }
}
