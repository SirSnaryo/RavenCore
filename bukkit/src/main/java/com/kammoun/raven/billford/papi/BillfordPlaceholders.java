package com.kammoun.raven.billford.papi;

import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.manager.BillfordManager;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * %billford_current%   — the active preset name (or "none")
 * %billford_timeleft%  — human-readable time until the next rotation
 * %billford_count%     — number of presets on the network
 */
public class BillfordPlaceholders extends KPlaceholderExpansion {

    public BillfordPlaceholders(JavaPlugin plugin, BillfordManager manager) {
        super(plugin, "billford");
        register("current", player -> {
            String current = manager.getCurrentPresetName();
            return current != null ? current : "none";
        });
        register("timeleft", player -> BillfordConfig.formatDuration(manager.timeUntilNextChange()));
        register("count", player -> String.valueOf(manager.getPresets().size()));
    }
}
