package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrivateMessageDeliveredEvent extends CrossServerEvent {
    private String requestId;
    private String senderUuid;
    private String targetUuid;
}
