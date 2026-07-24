package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CombatTagEvent extends CrossServerEvent {
    private String playerUuid;
    private String opponentUuid;
    private long expiresAt;
}
