package com.kammoun.raven.punishment;

import java.util.Locale;
import java.util.OptionalLong;

public final class PunishmentDurationParser {

    private PunishmentDurationParser() {
    }

    public static OptionalLong parseMillis(String input) {
        if (input == null || input.isBlank()) return OptionalLong.empty();
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.equals("perm") || value.equals("permanent") || value.equals("forever")) {
            return OptionalLong.empty();
        }

        char unit = value.charAt(value.length() - 1);
        String number = value.substring(0, value.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> 0L;
        };
        if (multiplier == 0L || number.isBlank()) return OptionalLong.empty();
        try {
            long amount = Long.parseLong(number);
            return amount <= 0L ? OptionalLong.empty() : OptionalLong.of(amount * multiplier);
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}
