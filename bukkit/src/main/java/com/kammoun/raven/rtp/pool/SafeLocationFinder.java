package com.kammoun.raven.rtp.pool;

import com.kammoun.raven.portal.model.OverworldRegion;
import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import com.kammoun.raven.rtp.config.RtpPoolConfig;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks one random, landable location in a world. Candidate x/z coordinates are
 * arbitrary and usually far from any already-loaded chunk, so chunk access must go
 * through {@link World#getChunkAtAsync(int, int, boolean)} rather than a synchronous
 * height/block lookup: on Folia, only that call both loads the target chunk and
 * completes its future on the region thread that actually owns it — the only thread
 * allowed to then read block/height data there. A plain region-scheduler task keyed
 * off some other location (e.g. spawn) runs on the wrong thread for a distant
 * candidate and throws.
 *
 * <p>When the destination maps to a portal {@link OverworldRegion}, candidates are drawn
 * uniformly from that region's box so a pick can never land in an adjacent server's
 * half of the shared coordinate space — otherwise the border enforcer would bounce the
 * teleported player straight over to the neighbouring server. Destinations without a
 * region (e.g. the dedicated End) fall back to a ring between min/max-radius. Either way
 * the usable area is clamped to the world border.
 */
public final class SafeLocationFinder {

    /** Blocks kept clear of the border edge so a pick never resolves right on the wall. */
    private static final double BORDER_MARGIN = 16;

    private SafeLocationFinder() {}

    /**
     * {@code bounds} supplies the per-destination radius/Y range and {@code config} the shared
     * attempt/unsafe-block settings. {@code region} is the destination's portal region box, or
     * {@code null} to sample by radius around the world-border centre instead.
     */
    public static CompletableFuture<Optional<PooledLocation>> find(World world, RegionDestination bounds,
                                                                   RtpPoolConfig config, OverworldRegion region) {
        return attempt(world, bounds, config, region, config.maxAttemptsPerLocation());
    }

    private static CompletableFuture<Optional<PooledLocation>> attempt(World world, RegionDestination bounds,
                                                                        RtpPoolConfig config, OverworldRegion region,
                                                                        int attemptsLeft) {
        if (attemptsLeft <= 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        int[] xz = pickCandidate(world, bounds, region);
        if (xz == null) {
            // Usable area is empty (region box outside the border, or a zero radius) — nothing to hand out.
            return CompletableFuture.completedFuture(Optional.empty());
        }
        int x = xz[0];
        int z = xz[1];

        return world.getChunkAtAsync(x >> 4, z >> 4, true).thenCompose(chunk -> {
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < bounds.minY() || y > bounds.maxY()) {
                return attempt(world, bounds, config, region, attemptsLeft - 1);
            }

            Block landing = world.getBlockAt(x, y, z);
            if (!isSafeLanding(landing, config)) {
                return attempt(world, bounds, config, region, attemptsLeft - 1);
            }

            Block feet = landing.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            if (!isPassable(feet) || !isPassable(head)) {
                return attempt(world, bounds, config, region, attemptsLeft - 1);
            }

            float yaw = ThreadLocalRandom.current().nextFloat() * 360f;
            return CompletableFuture.completedFuture(
                    Optional.of(new PooledLocation(world.getName(), x + 0.5, y + 1, z + 0.5, yaw)));
        });
    }

    /**
     * Draws a candidate x/z clamped to the world border. With a {@code region} the pick is uniform
     * within that region's box (intersected with the border); without one it's a ring between the
     * destination's min/max-radius around the border centre. Returns {@code null} if the resulting
     * area is empty.
     */
    private static int[] pickCandidate(World world, RegionDestination bounds, OverworldRegion region) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WorldBorder border = world.getWorldBorder();
        double half = Math.max(0, border.getSize() / 2.0 - BORDER_MARGIN);
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        if (region != null) {
            double minX = Math.max(region.getMinX(), centerX - half);
            double maxX = Math.min(region.getMaxX(), centerX + half);
            double minZ = Math.max(region.getMinZ(), centerZ - half);
            double maxZ = Math.min(region.getMaxZ(), centerZ + half);
            if (minX >= maxX || minZ >= maxZ) return null;
            return new int[] {
                    (int) Math.floor(random.nextDouble(minX, maxX)),
                    (int) Math.floor(random.nextDouble(minZ, maxZ))
            };
        }

        // A circle of radius r fits inside a square border of half-size r, so clamping maxRadius to
        // the border keeps every radius-sampled pick in-bounds too.
        double maxRadius = Math.min(bounds.maxRadius(), half);
        double minRadius = Math.min(bounds.minRadius(), maxRadius);
        if (maxRadius <= 0) return null;

        double minRadiusSq = minRadius * minRadius;
        double maxRadiusSq = maxRadius * maxRadius;
        double angle = random.nextDouble(0, Math.PI * 2);
        double radius = Math.sqrt(random.nextDouble() * (maxRadiusSq - minRadiusSq) + minRadiusSq);
        return new int[] {
                (int) Math.floor(centerX + Math.cos(angle) * radius),
                (int) Math.floor(centerZ + Math.sin(angle) * radius)
        };
    }

    private static boolean isSafeLanding(Block block, RtpPoolConfig config) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return false;
        if (config.unsafeBlocks().contains(type)) return false;
        return type.isSolid();
    }

    private static boolean isPassable(Block block) {
        return block.isPassable() && !block.isLiquid();
    }
}
