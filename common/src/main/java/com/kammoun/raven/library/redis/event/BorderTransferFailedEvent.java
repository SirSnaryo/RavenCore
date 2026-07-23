package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BorderTransferFailedEvent extends CrossServerEvent {
    private String playerUuid;
    private String targetServer;
    private String reason;
}
