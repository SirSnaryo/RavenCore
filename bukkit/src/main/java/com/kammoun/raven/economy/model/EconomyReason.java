package com.kammoun.raven.economy.model;

public record EconomyReason(EconomyTransactionType type, String reason, String rawCommand) {

    public static EconomyReason playerPay(String rawCommand) {
        return new EconomyReason(EconomyTransactionType.PLAYER_PAY, "Player payment", rawCommand);
    }

    public static EconomyReason admin(EconomyTransactionType type, String reason, String rawCommand) {
        return new EconomyReason(type, reason == null || reason.isBlank() ? "Admin economy command" : reason, rawCommand);
    }

    public static EconomyReason vault(EconomyTransactionType type) {
        return new EconomyReason(type, "External Vault economy API", "Vault API");
    }

    public static EconomyReason system(String reason) {
        return new EconomyReason(EconomyTransactionType.SYSTEM, reason, "RavenCore");
    }
}
