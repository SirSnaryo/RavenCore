package com.kammoun.raven.mythictools.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(d|h|m|s)");

    private DurationParser() {}

    public static long parseMillis(String input) {
        if (input == null) return -1L;
        String value = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PART.matcher(value);
        long total = 0L;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) return -1L;
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return -1L;
            }
            try {
                total = Math.addExact(total, Math.multiplyExact(amount, switch (matcher.group(2)) {
                    case "d" -> 86_400_000L;
                    case "h" -> 3_600_000L;
                    case "m" -> 60_000L;
                    default -> 1_000L;
                }));
            } catch (ArithmeticException ignored) {
                return -1L;
            }
            consumed = matcher.end();
        }
        return consumed == value.length() && total > 0L ? total : -1L;
    }

    public static String format(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remaining = seconds % 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remaining + "s";
        return remaining + "s";
    }
}
