package com.kammoun.raven.billford.papi;

import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.manager.BillfordManager;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * %billford_current%   — the active preset name (or "none")
 * %billford_timeleft%  — human-readable time until the next rotation
 * %billford_next_change_date% — server-local date/time for the next rotation
 * %billford_count%     — number of presets on the network
 */
public class BillfordPlaceholders extends KPlaceholderExpansion {

    private static final DateTimeFormatter NEXT_CHANGE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public BillfordPlaceholders(JavaPlugin plugin, BillfordManager manager) {
        super(plugin, "billford");
        register("current", player -> {
            String current = manager.getCurrentPresetName();
            return current != null ? current : "none";
        });
        register("timeleft", player -> BillfordConfig.formatDuration(manager.timeUntilNextChange()));
        register("next_change_date", player -> formatNextChange(manager.nextChangeAtMillis()));
        register("count", player -> String.valueOf(manager.getPresets().size()));
    }

    private String formatNextChange(long millis) {
        if (millis <= 0L) {
            return "none";
        }
        return NEXT_CHANGE_FORMAT.format(Instant.ofEpochMilli(millis));
    }
}
