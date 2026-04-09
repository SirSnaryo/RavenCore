package com.kammoun.raven.library.network;

import lombok.Getter;

import java.util.UUID;

@Getter
public class NetworkPlayerInfo {

    private final UUID uuid;
    private final String playerName;
    private final String serverId;

    public NetworkPlayerInfo(UUID uuid, String playerName, String serverId) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.serverId = serverId;
    }

}
