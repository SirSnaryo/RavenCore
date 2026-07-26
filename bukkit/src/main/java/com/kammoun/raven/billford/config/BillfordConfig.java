package com.kammoun.raven.billford.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Billford behavior comes from config.yml while all menu layout comes from gui.yml.
 */
@Getter
public class BillfordConfig {

    private final FileConfiguration config;
    private final FileConfiguration guiConfig;

    private final String intervalRaw;
    private final long intervalMillis;

    private final String guiTitle;
    private final int guiSize;
    private final List<Integer> requiredSlots;
    private final int tradeSlot;
    private final String editorTitle;

    public BillfordConfig(FileConfiguration config, FileConfiguration guiConfig) {
        this.config = config;
        this.guiConfig = guiConfig;
        this.intervalRaw = config.getString("preset-change-interval", "2m");
        this.intervalMillis = parseInterval(intervalRaw);
        this.guiTitle = guiConfig.getString("gui.title", "&#FFD447Billford");
        this.guiSize = guiConfig.getInt("gui.size", 54);
        this.requiredSlots = guiConfig.getIntegerList("gui.required-items-slots");
        this.tradeSlot = guiConfig.getInt("gui.trade-slot", 25);
        this.editorTitle = guiConfig.getString("editor.title", "&#FFD447Editing: &#FFFFFF%preset%");
    }

    public static long parseInterval(String interval) {
        return tryParseInterval(interval).orElse(TimeUnit.MINUTES.toMillis(2));
    }

    public static OptionalLong tryParseInterval(String interval) {
        if (interval == null) return OptionalLong.empty();
        String trimmed = interval.toLowerCase().trim();
        if (!trimmed.matches("^[0-9]+[smhd]$")) return OptionalLong.empty();
        try {
            long value = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
            if (value <= 0) return OptionalLong.empty();
            long millis = switch (trimmed.charAt(trimmed.length() - 1)) {
                case 's' -> Math.multiplyExact(value, 1_000L);
                case 'm' -> Math.multiplyExact(value, 60_000L);
                case 'h' -> Math.multiplyExact(value, 3_600_000L);
                case 'd' -> Math.multiplyExact(value, 86_400_000L);
                default -> throw new IllegalArgumentException("Unsupported interval unit");
            };
            return OptionalLong.of(millis);
        } catch (ArithmeticException | NumberFormatException error) {
            return OptionalLong.empty();
        }
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
