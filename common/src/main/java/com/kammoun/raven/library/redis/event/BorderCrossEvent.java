package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BorderCrossEvent extends CrossServerEvent {
    private String playerUuid;
    private String targetServer;
    private String dimension; // "NORMAL" | "NETHER"
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    /**
     * Optional Continuum seamless-switch metadata. Zero means unavailable or disabled.
     */
    private int continuumClientEntityId;
    /**
     * Optional target dimension hint; Continuum still validates the real client dimension itself.
     */
    private String continuumDimensionKey;
}
