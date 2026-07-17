package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageEvent extends CrossServerEvent {
    private String senderUuid;
    private String formattedMessage;
}
