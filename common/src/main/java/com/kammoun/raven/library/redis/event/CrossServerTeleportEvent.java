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
}
