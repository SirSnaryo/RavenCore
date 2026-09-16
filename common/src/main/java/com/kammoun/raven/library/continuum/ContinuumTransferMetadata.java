package com.kammoun.raven.library.continuum;

public record ContinuumTransferMetadata(
        int clientEntityId,
        String targetServer,
        String dimensionKey,
        long createdAt
) {
    public boolean usableFor(String serverId, long maxAgeMillis) {
        return clientEntityId > 0
                && targetServer != null
                && !targetServer.isBlank()
                && targetServer.equalsIgnoreCase(serverId)
                && createdAt > 0
                && System.currentTimeMillis() - createdAt <= maxAgeMillis;
    }
}
