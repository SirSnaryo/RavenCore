package com.kammoun.raven.economy.util;

import java.util.Locale;

public final class EconomyAmountParser {

    private EconomyAmountParser() {
    }

    public static double parse(String input) {
        if (input == null || input.isBlank()) {
            throw new NumberFormatException("empty amount");
        }

        String normalized = input.trim().toLowerCase(Locale.US).replace(",", "");
        double multiplier = 1D;
        char suffix = normalized.charAt(normalized.length() - 1);
        if (Character.isLetter(suffix)) {
            multiplier = switch (suffix) {
                case 'k' -> 1_000D;
                case 'm' -> 1_000_000D;
                case 'b' -> 1_000_000_000D;
                case 't' -> 1_000_000_000_000D;
                default -> throw new NumberFormatException("unknown suffix");
            };
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        double value = Double.parseDouble(normalized) * multiplier;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new NumberFormatException("invalid amount");
        }
        return value;
    }
}
