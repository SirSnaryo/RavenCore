package com.kammoun.raven.library.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkLocation {

    private String serverName;
    private LocationType type;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private int maxPlayers;
    private boolean enabled;
    private long updatedAt;
}
