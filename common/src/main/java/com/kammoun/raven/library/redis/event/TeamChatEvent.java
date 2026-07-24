package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamChatEvent extends CrossServerEvent {
    private String teamId;
    private String senderUuid;
    private String senderName;
    private String message;
}
