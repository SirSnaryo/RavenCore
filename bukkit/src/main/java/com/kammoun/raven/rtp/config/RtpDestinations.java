package com.kammoun.raven.rtp.config;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RTP's own server list — deliberately separate from the portal module's
 * overworld-regions, so RTP's world choices/labels can diverge from portal routing.
 * Each destination carries its own random-search bounds, since NA/EU/Nether/End are
 * different worlds with different sane radius/Y ranges. A survival server backs two
 * destinations at once (its Overworld region and that region's local Nether), so
 * destinations are keyed by their own {@code id} rather than by server.
 */
public final class RtpDestinations {

    public record RegionDestination(String id, String server, String displayName, World.Environment environment,
                                     double minRadius, double maxRadius, int minY, int maxY) {}

    private final Map<String, RegionDestination> overworldWorlds;
    private final Map<String, RegionDestination> netherWorlds;
    private final RegionDestination end;

    private RtpDestinations(Map<String, RegionDestination> overworldWorlds,
                             Map<String, RegionDestination> netherWorlds, RegionDestination end) {
        this.overworldWorlds = overworldWorlds;
        this.netherWorlds = netherWorlds;
        this.end = end;
    }

    public static RtpDestinations load(FileConfiguration config) {
        Map<String, RegionDestination> overworld = loadRegionMap(config, "overworld-worlds", World.Environment.NORMAL, "");
        Map<String, RegionDestination> nether = loadRegionMap(config, "nether-worlds", World.Environment.NETHER, "nether_");
        RegionDestination end = readDestination(config.getConfigurationSection("end"), "end", "End", World.Environment.THE_END);

        return new RtpDestinations(overworld, nether, end);
    }

    /**
     * The Overworld and Nether region sections naturally reuse the same keys ({@code na}, {@code eu}),
     * and now that a region's Nether lives on the same server as its Overworld, an un-namespaced id
     * would make both share one Redis pool ({@code raven:rtp:pool:na}) — so a Nether request could
     * hand out an Overworld location and vice versa. {@code idPrefix} keeps each dimension's pool
     * distinct while the display name still comes from the bare config key.
     */
    private static Map<String, RegionDestination> loadRegionMap(FileConfiguration config, String path,
                                                                World.Environment environment, String idPrefix) {
        Map<String, RegionDestination> regions = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return regions;

        for (String key : section.getKeys(false)) {
            String id = idPrefix + key;
            RegionDestination dest = readDestination(section.getConfigurationSection(key), id, key.toUpperCase(), environment);
            if (dest != null) regions.put(id, dest);
        }
        return regions;
    }

    private static RegionDestination readDestination(ConfigurationSection section, String id,
                                                       String defaultDisplayName, World.Environment environment) {
        if (section == null) return null;
        String server = section.getString("server");
        if (server == null) return null;

        return new RegionDestination(
                id, server, section.getString("display-name", defaultDisplayName), environment,
                section.getDouble("min-radius", 500),
                section.getDouble("max-radius", 10000),
                section.getInt("min-y", -64),
                section.getInt("max-y", 320)
        );
    }

    public Collection<RegionDestination> overworldWorlds() {
        return Collections.unmodifiableCollection(overworldWorlds.values());
    }

    public Collection<RegionDestination> netherWorlds() {
        return Collections.unmodifiableCollection(netherWorlds.values());
    }

    public Optional<RegionDestination> end() {
        return Optional.ofNullable(end);
    }

    public String endServer() {
        return end != null ? end.server() : "";
    }

    /** Every destination hosted on {@code serverId} — a survival server backs both an Overworld region and its local Nether. */
    public List<RegionDestination> findAllByServer(String serverId) {
        List<RegionDestination> found = new ArrayList<>();
        if (end != null && end.server().equals(serverId)) found.add(end);
        overworldWorlds.values().stream().filter(r -> r.server().equals(serverId)).forEach(found::add);
        netherWorlds.values().stream().filter(r -> r.server().equals(serverId)).forEach(found::add);
        return found;
    }
}
