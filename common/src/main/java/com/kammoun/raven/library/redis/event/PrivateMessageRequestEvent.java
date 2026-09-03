package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrivateMessageRequestEvent extends CrossServerEvent {
    private String requestId;
    private String senderUuid;
    private String senderName;
    private String targetUuid;
    private String targetName;
    private String message;
}
