package com.kammoun.raven.borders.config;

import com.kammoun.raven.borders.utils.GridCell;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public class BorderConfig {

    private static final String DEFAULT_TRANSFER_FAILURE_MESSAGE =
            "&#D90429Border transfer failed. Please reconnect.";

    private final int cellSize;
    private final int netherRatio;
    private final int cellX;
    private final int cellZ;
    private final String cluster;
    private final String denyMessage;
    private final double detectionDistance;
    private final double particleSpacing;
    private final double wallHeight;
    private final Particle particle;

    public BorderConfig(FileConfiguration root) {
        this.cellSize    = root.getInt("grid.cell-size", 5000);
        this.netherRatio = root.getInt("grid.nether-ratio", 8);
        this.cellX       = root.getInt("grid.cell-x", 0);
        this.cellZ       = root.getInt("grid.cell-z", 0);
        this.cluster     = root.getString("network.cluster", "default");
        this.denyMessage = root.getString("border.deny-message", "");
        this.detectionDistance = Math.max(0, root.getDouble("border.detection-distance", 30));
        this.particleSpacing = Math.max(0.25, root.getDouble("border.particle-spacing", 1.5));
        this.wallHeight = Math.max(0, root.getDouble("border.wall-height", 50));
        this.particle = parseParticle(root.getString("border.particle", "DUST_PLUME"));
    }

    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
    public String cluster() { return cluster; }
    public String denyMessage() { return denyMessage; }
    public String transferFailureMessage() {
        return denyMessage == null || denyMessage.isBlank() ? DEFAULT_TRANSFER_FAILURE_MESSAGE : denyMessage;
    }
    public double detectionDistance() { return detectionDistance; }
    public double particleSpacing() { return particleSpacing; }
    public double wallHeight() { return wallHeight; }
    public Particle particle() { return particle; }

    /** Returns the cell boundary for the given dimension, or null for unsupported dimensions. */
    public GridCell cellFor(World.Environment env) {
        if (env == World.Environment.NORMAL || env == World.Environment.NETHER) {
            return GridCell.of(cellSize, netherRatio, cellX, cellZ, env);
        }
        return null;
    }

    private static Particle parseParticle(String configured) {
        try {
            Particle particle = Particle.valueOf(configured.toUpperCase(Locale.ROOT));
            return particle.getDataType() == Void.class ? particle : Particle.DUST_PLUME;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Particle.DUST_PLUME;
        }
    }
}
