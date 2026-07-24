package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamStateChangedEvent extends CrossServerEvent {
    private String teamId;
}
