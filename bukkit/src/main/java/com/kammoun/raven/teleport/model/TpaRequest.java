package com.kammoun.raven.teleport.model;

import java.util.UUID;

public record TpaRequest(
        UUID requesterId,
        String requesterName,
        String requesterServer,
        String requesterServerDisplayName,
        UUID targetId,
        String targetName,
        String targetServer,
        String targetServerDisplayName,
        Type type,
        long createdAt
) {

    public TpaRequest(UUID requesterId, String requesterName, String requesterServer,
                      UUID targetId, String targetName, String targetServer, Type type) {
        this(requesterId, requesterName, requesterServer, requesterServer,
                targetId, targetName, targetServer, targetServer, type,
                System.currentTimeMillis());
    }

    public TpaRequest(UUID requesterId, String requesterName, String requesterServer,
                      UUID targetId, String targetName, String targetServer, Type type, long createdAt) {
        this(requesterId, requesterName, requesterServer, requesterServer,
                targetId, targetName, targetServer, targetServer, type, createdAt);
    }

    public TpaRequest(UUID requesterId, String requesterName, String requesterServer, String requesterServerDisplayName,
                      UUID targetId, String targetName, String targetServer, String targetServerDisplayName,
                      Type type) {
        this(requesterId, requesterName, requesterServer, requesterServerDisplayName,
                targetId, targetName, targetServer, targetServerDisplayName, type,
                System.currentTimeMillis());
    }

    public boolean isExpired(long clearTimeSeconds) {
        return isExpired(clearTimeSeconds, System.currentTimeMillis());
    }

    public boolean isExpired(long clearTimeSeconds, long now) {
        return clearTimeSeconds >= 0 && now - createdAt > clearTimeSeconds * 1000L;
    }

    public enum Type {
        TPA,
        TPA_HERE
    }
}
