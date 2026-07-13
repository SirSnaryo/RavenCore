package com.kammoun.raven.rtp.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

/**
 * Typed view of the pool-wide settings in the {@code pool:} section of rtp/config.yml.
 * Per-destination search bounds (radius/Y range) live on {@link RtpDestinations.RegionDestination}
 * instead, since they differ per world.
 */
public final class RtpPoolConfig {

    private final int targetSize;
    private final int refillThreshold;
    private final int refillIntervalSeconds;
    private final int maxAttemptsPerLocation;
    private final Set<Material> unsafeBlocks;

    private RtpPoolConfig(int targetSize, int refillThreshold, int refillIntervalSeconds,
                           int maxAttemptsPerLocation, Set<Material> unsafeBlocks) {
        this.targetSize = targetSize;
        this.refillThreshold = refillThreshold;
        this.refillIntervalSeconds = refillIntervalSeconds;
        this.maxAttemptsPerLocation = maxAttemptsPerLocation;
        this.unsafeBlocks = unsafeBlocks;
    }

    public static RtpPoolConfig load(FileConfiguration config) {
        Set<Material> unsafe = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("pool.unsafe-blocks")) {
            try {
                unsafe.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                org.bukkit.Bukkit.getLogger().warning("[Rtp] Unknown material in pool.unsafe-blocks: " + name);
            }
        }

        return new RtpPoolConfig(
                config.getInt("pool.target-size", 50),
                config.getInt("pool.refill-threshold", 15),
                config.getInt("pool.refill-interval-seconds", 30),
                config.getInt("pool.max-attempts-per-location", 10),
                unsafe
        );
    }

    public int targetSize() { return targetSize; }
    public int refillThreshold() { return refillThreshold; }
    public int refillIntervalSeconds() { return refillIntervalSeconds; }
    public int maxAttemptsPerLocation() { return maxAttemptsPerLocation; }
    public Set<Material> unsafeBlocks() { return unsafeBlocks; }
}
