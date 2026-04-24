package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentNotificationEvent extends CrossServerEvent {
    private String recipientUuid;
    private String message;
}
