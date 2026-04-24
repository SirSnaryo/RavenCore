package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkPlayerQuitEvent extends CrossServerEvent {
    private String uuid;
}
