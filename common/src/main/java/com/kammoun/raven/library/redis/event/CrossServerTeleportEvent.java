package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrossServerTeleportEvent extends CrossServerEvent {
    /** The player being moved. */
    private String playerUuid;
    /** The player they should be teleported to once on the same server. */
    private String destinationUuid;
    /** Backend server where the moving player must arrive before Bukkit applies the teleport. */
    private String targetServer;
    /** Optional target world. If empty, the target server's join world is used. */
    private String world;
    private Double x;
    private Double y;
    private Double z;
    private Float yaw;
    private Float pitch;
    /**
     * Optional Continuum seamless-switch metadata. Zero means unavailable or disabled.
     */
    private int continuumClientEntityId;
    /**
     * Optional target dimension hint; Continuum still validates the real client dimension itself.
     */
    private String continuumDimensionKey;

    public boolean hasCoordinates() {
        return x != null && y != null && z != null;
    }
}
