package com.kammoun.raven.rtp.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RtpConfig(int cooldownSeconds, int warmupSeconds, boolean cancelOnMove,
                        boolean cancelOnDamage, String endServer,
                        Map<String, String> overworldClusters) {

    public static RtpConfig load(FileConfiguration config) {
        Map<String, String> clusters = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("overworld-clusters");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String display = section.getString(id + ".display-name", id);
                clusters.put(id, display == null || display.isBlank() ? id : display);
            }
        }
        return new RtpConfig(
                Math.max(0, config.getInt("cooldown-seconds", 5)),
                Math.max(0, config.getInt("warmup-seconds", 3)),
                config.getBoolean("cancel-on-move", true),
                config.getBoolean("cancel-on-damage", true),
                config.getString("end-server", "smp-end").trim(),
                Collections.unmodifiableMap(clusters));
    }
}
