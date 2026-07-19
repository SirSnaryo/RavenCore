package com.kammoun.raven.billford.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scalar Billford settings pulled from the module's config.yml. GUIs read their item
 * sections straight off {@link #getConfig()} via ItemReader; this class only owns the
 * parsed values (rotation interval, GUI layout).
 */
@Getter
public class BillfordConfig {

    private final FileConfiguration config;

    private final String intervalRaw;
    private final long intervalMillis;

    private final String guiTitle;
    private final int guiSize;
    private final List<Integer> requiredSlots;
    private final int tradeSlot;
    private final String editorTitle;

    public BillfordConfig(FileConfiguration config) {
        this.config = config;
        this.intervalRaw = config.getString("preset-change-interval", "2m");
        this.intervalMillis = parseInterval(intervalRaw);
        this.guiTitle = config.getString("gui.title", "&#FFD447Billford");
        this.guiSize = config.getInt("gui.size", 54);
        this.requiredSlots = config.getIntegerList("gui.required-items-slots");
        this.tradeSlot = config.getInt("gui.trade-slot", 25);
        this.editorTitle = config.getString("editor.title", "&#FFD447Editing: &#FFFFFF%preset%");
    }

    public static long parseInterval(String interval) {
        if (interval == null || interval.isEmpty()) return TimeUnit.MINUTES.toMillis(2);
        String trimmed = interval.toLowerCase().trim();
        String number = trimmed.replaceAll("[^0-9]", "");
        String unit = trimmed.replaceAll("[0-9]", "");
        long value;
        try {
            value = Long.parseLong(number);
        } catch (NumberFormatException e) {
            return TimeUnit.MINUTES.toMillis(2);
        }
        return switch (unit) {
            case "s" -> TimeUnit.SECONDS.toMillis(value);
            case "h" -> TimeUnit.HOURS.toMillis(value);
            case "d" -> TimeUnit.DAYS.toMillis(value);
            default -> TimeUnit.MINUTES.toMillis(value);
        };
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
