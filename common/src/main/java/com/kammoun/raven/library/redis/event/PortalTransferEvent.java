package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PortalTransferEvent extends CrossServerEvent {
    private String uuid;
    private double x;
    private double y;
    private double z;
}
