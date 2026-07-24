package com.kammoun.raven.economy.model;

import java.util.UUID;

public record EconomyTransactionResult(
        boolean success,
        String transactionId,
        UUID primaryUuid,
        UUID secondaryUuid,
        double amount,
        double primaryBefore,
        double primaryAfter,
        double secondaryBefore,
        double secondaryAfter,
        String failureReason
) {

    public static EconomyTransactionResult failure(UUID primaryUuid, UUID secondaryUuid, double amount, String failureReason) {
        return new EconomyTransactionResult(false, null, primaryUuid, secondaryUuid, amount, 0D, 0D, 0D, 0D, failureReason);
    }
}
