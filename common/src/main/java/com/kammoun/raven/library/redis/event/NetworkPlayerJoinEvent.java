package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkPlayerJoinEvent extends CrossServerEvent {
    private String uuid;
    private String playerName;
}
