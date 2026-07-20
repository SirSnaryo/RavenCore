package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatBroadcastEvent extends CrossServerEvent {
    private String message;
    private String actionBar;
    private String soundKey;
}
