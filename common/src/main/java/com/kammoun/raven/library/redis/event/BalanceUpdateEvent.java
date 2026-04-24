package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BalanceUpdateEvent extends CrossServerEvent {
    private String uuid;
    private double newBalance;
    private double delta;
}
