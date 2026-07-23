package com.kammoun.raven.locationPool.config;

import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationSearchBounds;
import com.kammoun.raven.library.platform.ServerRole;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class LocationPoolConfig {

    private static final int TARGET_SIZE = 50;

    private final long refillIntervalTicks;
    private final boolean netherEnabled;
    private final Map<LocationDimension, LocationSearchBounds> bounds;
    private final Set<Material> blacklistedBlocks;
    private final Set<Biome> blacklistedBiomes;

    private LocationPoolConfig(long refillIntervalTicks, boolean netherEnabled,
                               Map<LocationDimension, LocationSearchBounds> bounds,
                               Set<Material> blacklistedBlocks, Set<Biome> blacklistedBiomes) {
        this.refillIntervalTicks = refillIntervalTicks;
        this.netherEnabled = netherEnabled;
        this.bounds = Map.copyOf(bounds);
        this.blacklistedBlocks = Set.copyOf(blacklistedBlocks);
        this.blacklistedBiomes = Set.copyOf(blacklistedBiomes);
    }

    public static LocationPoolConfig load(FileConfiguration module, FileConfiguration root,
                                          Logger logger, ServerRole role) {
        long refillTicks = Math.max(20L,
                module.getLong("location-library.refill-interval-seconds", 45L) * 20L);
        boolean netherEnabled = module.getBoolean("location-library.nether-pool-enabled", true);

        int cellSize = root.getInt("grid.cell-size", 5000);
        int netherRatio = Math.max(1, root.getInt("grid.nether-ratio", 8));
        int cellX = root.getInt("grid.cell-x", 0);
        int cellZ = root.getInt("grid.cell-z", 0);
        int margin = Math.max(0, root.getInt("grid.border-safety-margin", 300));

        Map<LocationDimension, LocationSearchBounds> bounds = new EnumMap<>(LocationDimension.class);
        if (role == ServerRole.SURVIVAL) {
            LocationSearchBounds overworld = readBounds(module, "overworld");
            validateInsideGrid(overworld, GridCell.of(cellSize, netherRatio, cellX, cellZ, World.Environment.NORMAL),
                    margin, "overworld");
            bounds.put(LocationDimension.OVERWORLD, overworld);

            if (netherEnabled) {
                LocationSearchBounds nether = readBounds(module, "nether");
                int netherMargin = (int) Math.ceil(margin / (double) netherRatio);
                validateInsideGrid(nether, GridCell.of(cellSize, netherRatio, cellX, cellZ, World.Environment.NETHER),
                        netherMargin, "nether");
                bounds.put(LocationDimension.NETHER, nether);
            }
        } else if (role == ServerRole.END) {
            bounds.put(LocationDimension.END, readBounds(module, "end"));
        }

        return new LocationPoolConfig(refillTicks, netherEnabled,
                bounds, parseMaterials(module, logger), parseBiomes(module, logger));
    }

    private static LocationSearchBounds readBounds(FileConfiguration root, String name) {
        String path = "location-library.bounds." + name;
        for (String coordinate : new String[]{"min.x", "min.y", "min.z", "max.x", "max.y", "max.z"}) {
            if (!root.contains(path + "." + coordinate)) {
                throw new IllegalArgumentException("Missing LocationPool bound: " + path + "." + coordinate);
            }
        }
        return new LocationSearchBounds(
                root.getInt(path + ".min.x"), root.getInt(path + ".min.y"), root.getInt(path + ".min.z"),
                root.getInt(path + ".max.x"), root.getInt(path + ".max.y"), root.getInt(path + ".max.z"));
    }

    private static void validateInsideGrid(LocationSearchBounds bounds, GridCell cell,
                                           int margin, String dimension) {
        long safeMinX = cell.minX() + margin;
        long safeMinZ = cell.minZ() + margin;
        long safeMaxX = cell.maxX() - margin;
        long safeMaxZ = cell.maxZ() - margin;
        if (safeMinX >= safeMaxX || safeMinZ >= safeMaxZ) {
            throw new IllegalArgumentException("Grid safety margin leaves no " + dimension + " search area");
        }
        if (!bounds.fitsWithin(safeMinX, safeMinZ, safeMaxX, safeMaxZ)) {
            throw new IllegalArgumentException("LocationPool " + dimension
                    + " X/Z bounds exceed the safe grid interior [" + safeMinX + "," + safeMinZ
                    + "] -> [" + safeMaxX + "," + safeMaxZ + "]");
        }
    }

    private static Set<Material> parseMaterials(FileConfiguration root, Logger logger) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String name : root.getStringList("location-library.blacklisted-blocks")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                logger.warning("[LocationPool] Ignoring unknown blacklisted material: " + name);
            } else {
                result.add(material);
            }
        }
        return result;
    }

    private static Set<Biome> parseBiomes(FileConfiguration root, Logger logger) {
        Set<Biome> result = new HashSet<>();
        for (String name : root.getStringList("location-library.blacklisted-biomes")) {
            try {
                result.add(Biome.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                logger.warning("[LocationPool] Ignoring unknown blacklisted biome: " + name);
            }
        }
        return result;
    }

    public int targetSize() { return TARGET_SIZE; }
    public long refillIntervalTicks() { return refillIntervalTicks; }
    public boolean netherEnabled() { return netherEnabled; }
    public LocationSearchBounds bounds(LocationDimension dimension) { return bounds.get(dimension); }
    public Set<LocationDimension> dimensions() { return bounds.keySet(); }
    public Set<Material> blacklistedBlocks() { return blacklistedBlocks; }
    public Set<Biome> blacklistedBiomes() { return blacklistedBiomes; }
}
