package com.kammoun.raven.portal.model;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

@Getter
public class OverworldRegion {

    private final String server;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;

    public OverworldRegion(String server, double minX, double maxX, double minZ, double maxZ) {
        this.server = server;
        this.minX   = minX;
        this.maxX   = maxX;
        this.minZ   = minZ;
        this.maxZ   = maxZ;
    }

    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Loads every region under the config's {@code overworld-regions} section. */
    public static List<OverworldRegion> loadAll(FileConfiguration config) {
        List<OverworldRegion> regions = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("overworld-regions");
        if (section == null) return regions;
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) continue;
            regions.add(new OverworldRegion(
                    r.getString("server"),
                    r.getDouble("min-x"), r.getDouble("max-x"),
                    r.getDouble("min-z"), r.getDouble("max-z")
            ));
        }
        return regions;
    }

}
