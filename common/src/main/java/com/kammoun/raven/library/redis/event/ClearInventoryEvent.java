package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClearInventoryEvent extends CrossServerEvent {
    private String targetUuid;
    private String senderName;
}
