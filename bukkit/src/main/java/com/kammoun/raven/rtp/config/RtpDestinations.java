package com.kammoun.raven.rtp.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RTP's own server list — deliberately separate from the portal module's
 * overworld-regions, so RTP's world choices/labels can diverge from portal routing.
 * Each destination carries its own random-search bounds, since NA/EU/Nether/End are
 * different worlds with different sane radius/Y ranges.
 */
public final class RtpDestinations {

    public record RegionDestination(String id, String server, String displayName,
                                     double minRadius, double maxRadius, int minY, int maxY) {}

    private final Map<String, RegionDestination> overworldWorlds;
    private final RegionDestination nether;
    private final RegionDestination end;

    private RtpDestinations(Map<String, RegionDestination> overworldWorlds, RegionDestination nether, RegionDestination end) {
        this.overworldWorlds = overworldWorlds;
        this.nether = nether;
        this.end = end;
    }

    public static RtpDestinations load(FileConfiguration config) {
        Map<String, RegionDestination> regions = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("overworld-worlds");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                RegionDestination dest = readDestination(section.getConfigurationSection(id), id, id.toUpperCase());
                if (dest != null) regions.put(id, dest);
            }
        }

        RegionDestination nether = readDestination(config.getConfigurationSection("nether"), "nether", "Nether");
        RegionDestination end = readDestination(config.getConfigurationSection("end"), "end", "End");

        return new RtpDestinations(regions, nether, end);
    }

    private static RegionDestination readDestination(ConfigurationSection section, String id, String defaultDisplayName) {
        if (section == null) return null;
        String server = section.getString("server");
        if (server == null) return null;

        return new RegionDestination(
                id, server, section.getString("display-name", defaultDisplayName),
                section.getDouble("min-radius", 500),
                section.getDouble("max-radius", 10000),
                section.getInt("min-y", -64),
                section.getInt("max-y", 320)
        );
    }

    public Collection<RegionDestination> overworldWorlds() {
        return Collections.unmodifiableCollection(overworldWorlds.values());
    }

    public Optional<RegionDestination> overworldWorld(String id) {
        return Optional.ofNullable(overworldWorlds.get(id));
    }

    public Optional<RegionDestination> nether() {
        return Optional.ofNullable(nether);
    }

    public Optional<RegionDestination> end() {
        return Optional.ofNullable(end);
    }

    public String netherServer() {
        return nether != null ? nether.server() : "";
    }

    public String endServer() {
        return end != null ? end.server() : "";
    }

    /** Finds the destination entry (if any) whose server id matches — used by a server to find its own bounds. */
    public Optional<RegionDestination> findByServer(String serverId) {
        if (nether != null && nether.server().equals(serverId)) return Optional.of(nether);
        if (end != null && end.server().equals(serverId)) return Optional.of(end);
        return overworldWorlds.values().stream().filter(r -> r.server().equals(serverId)).findFirst();
    }
}
