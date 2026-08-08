package com.kammoun.raven.endportal.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record EndPortalConfig(String endServer, List<String> returnClusters,
                              boolean detectPortalBlocks, long retriggerCooldownMillis) {

    public static EndPortalConfig load(FileConfiguration config) {
        List<String> clusters = config.getStringList("return-clusters").stream()
                .filter(cluster -> cluster != null && !cluster.isBlank())
                .map(String::trim)
                .toList();

        return new EndPortalConfig(
                config.getString("end-server", "smp-end").trim(),
                clusters,
                config.getBoolean("detect-portal-blocks", true),
                Math.max(0L, config.getLong("retrigger-cooldown-millis", 3000L)));
    }
}
