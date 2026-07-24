package com.kammoun.raven.economy.model;

import java.util.UUID;

public record EconomyTransactionLog(
        String transactionId,
        EconomyTransactionType type,
        UUID targetUuid,
        String targetName,
        UUID actorUuid,
        String actorName,
        double amount,
        double balanceBefore,
        double balanceAfter,
        String reason,
        String rawCommand,
        String serverId,
        String serverDisplayName,
        boolean success,
        String failureReason,
        long createdAt
) {
}
