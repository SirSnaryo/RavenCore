package com.kammoun.raven.economy.settings;

public record EconomyPlayerSettings(boolean acceptsPayments, boolean paymentMessages) {

    public static EconomyPlayerSettings defaults() {
        return new EconomyPlayerSettings(true, true);
    }
}
