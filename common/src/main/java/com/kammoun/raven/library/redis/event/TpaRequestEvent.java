package com.kammoun.raven.library.redis.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TpaRequestEvent extends CrossServerEvent {
    private String requesterUuid;
    private String requesterName;
    private String requesterServer;
    private String requesterServerDisplayName;
    private String targetUuid;
    private String targetName;
    private String targetServer;
    private String targetServerDisplayName;
    private String type;
}
