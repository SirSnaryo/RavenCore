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
    private final long originX;
    private final long originZ;
    private final String cluster;
    private final String denyMessage;
    private final boolean particleAnimationEnabled;
    private final long particleRenderPeriodTicks;
    private final double detectionDistance;
    private final double arrivalInsetBlocks;
    private final boolean cleanupClientEntitiesOnTransfer;
    private final double cleanupClientEntitiesRadius;
    private final int cleanupClientEntitiesMax;
    private final double particleSpacing;
    private final double wallHeight;
    private final int maxParticlesPerRender;
    private final Particle particle;

    public BorderConfig(FileConfiguration root) {
        this.cellSize    = root.getInt("grid.cell-size", 5000);
        this.netherRatio = root.getInt("grid.nether-ratio", 8);
        this.cellX       = root.getInt("grid.cell-x", 0);
        this.cellZ       = root.getInt("grid.cell-z", 0);
        this.originX     = root.getLong("grid.origin-x", 0L);
        this.originZ     = root.getLong("grid.origin-z", 0L);
        this.cluster     = root.getString("network.cluster", "default");
        this.denyMessage = root.getString("border.deny-message", "");
        this.particleAnimationEnabled = root.getBoolean("border.particles.enabled",
                root.getBoolean("border.animation-enabled", true));
        this.particleRenderPeriodTicks = Math.max(10L, root.getLong("border.particles.render-period-ticks", 40L));
        this.detectionDistance = Math.max(0, root.getDouble("border.detection-distance", 10));
        this.arrivalInsetBlocks = Math.max(0.001, root.getDouble("border.arrival-inset-blocks", 0.01));
        this.cleanupClientEntitiesOnTransfer = root.getBoolean("border.cleanup-client-entities-on-transfer", true);
        this.cleanupClientEntitiesRadius = Math.max(8.0, root.getDouble("border.cleanup-client-entities-radius", 48.0));
        this.cleanupClientEntitiesMax = Math.max(0, root.getInt("border.cleanup-client-entities-max", 64));
        this.particleSpacing = Math.max(0.25, root.getDouble("border.particle-spacing", 6.0));
        this.wallHeight = Math.max(0, root.getDouble("border.wall-height", 8));
        this.maxParticlesPerRender = Math.max(8, root.getInt("border.max-particles-per-render", 18));
        this.particle = parseParticle(root.getString("border.particle", "DUST_PLUME"));
    }

    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
    public int cellSize() { return cellSize; }
    public int netherRatio() { return netherRatio; }
    public long originX() { return originX; }
    public long originZ() { return originZ; }
    public String cluster() { return cluster; }
    public String denyMessage() { return denyMessage; }
    public String transferFailureMessage() {
        return denyMessage == null || denyMessage.isBlank() ? DEFAULT_TRANSFER_FAILURE_MESSAGE : denyMessage;
    }
    public boolean particleAnimationEnabled() { return particleAnimationEnabled; }
    public long particleRenderPeriodTicks() { return particleRenderPeriodTicks; }
    public double detectionDistance() { return detectionDistance; }
    public double arrivalInsetBlocks() { return arrivalInsetBlocks; }
    public boolean cleanupClientEntitiesOnTransfer() { return cleanupClientEntitiesOnTransfer; }
    public double cleanupClientEntitiesRadius() { return cleanupClientEntitiesRadius; }
    public int cleanupClientEntitiesMax() { return cleanupClientEntitiesMax; }
    public double particleSpacing() { return particleSpacing; }
    public double wallHeight() { return wallHeight; }
    public int maxParticlesPerRender() { return maxParticlesPerRender; }
    public Particle particle() { return particle; }

    /** Returns the cell boundary for the given dimension, or null for unsupported dimensions. */
    public GridCell cellFor(World.Environment env) {
        if (env == World.Environment.NORMAL || env == World.Environment.NETHER) {
            return GridCell.of(cellSize, netherRatio, cellX, cellZ, originX, originZ, env);
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
