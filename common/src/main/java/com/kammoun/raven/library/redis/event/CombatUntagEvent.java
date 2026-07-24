package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CombatUntagEvent extends CrossServerEvent {
    private String playerUuid;
    private boolean notify;
}
