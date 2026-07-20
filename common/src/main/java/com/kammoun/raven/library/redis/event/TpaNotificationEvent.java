package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TpaNotificationEvent extends CrossServerEvent {
    private String recipientUuid;
    private String messageKey;
    private String playerName;
}
