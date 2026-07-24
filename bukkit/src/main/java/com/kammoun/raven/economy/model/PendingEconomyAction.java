package com.kammoun.raven.economy.model;

import java.util.UUID;

public record PendingEconomyAction(
        String id,
        UUID actorUuid,
        String actorName,
        UUID targetUuid,
        String targetName,
        double amount,
        String reason,
        String rawCommand,
        long expiresAt
) {

    public boolean expired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
