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
import java.util.OptionalInt;
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
 * teleported player straight over to the neighbouring server. The world border is always the
 * hard bound, so a correctly-sized per-server world border alone already keeps picks off a
 * neighbour's territory; the region, when present, just tightens the box further. The dedicated
 * End has no border split and samples a ring between min/max-radius around the border centre.
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
            OptionalInt landingY = findLandingY(world, x, z, bounds, config);
            if (landingY.isEmpty()) {
                return attempt(world, bounds, config, region, attemptsLeft - 1);
            }

            float yaw = ThreadLocalRandom.current().nextFloat() * 360f;
            return CompletableFuture.completedFuture(Optional.of(
                    new PooledLocation(world.getName(), x + 0.5, landingY.getAsInt() + 1, z + 0.5, yaw)));
        });
    }

    /**
     * Finds a safe standing Y at {@code x/z} within the destination's Y band, or empty if none.
     * The Nether has a solid bedrock roof, so its surface heightmap always points at the ceiling —
     * there we scan downward for the first open pocket instead. Other worlds (Overworld, End) have
     * a real surface, so the motion-blocking heightmap gives the landing spot directly.
     */
    private static OptionalInt findLandingY(World world, int x, int z, RegionDestination bounds, RtpPoolConfig config) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            int top = Math.min(bounds.maxY(), world.getMaxHeight() - 1);
            for (int y = top; y >= bounds.minY(); y--) {
                if (isValidStand(world, x, y, z, config)) return OptionalInt.of(y);
            }
            return OptionalInt.empty();
        }

        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y < bounds.minY() || y > bounds.maxY()) return OptionalInt.empty();
        return isValidStand(world, x, y, z, config) ? OptionalInt.of(y) : OptionalInt.empty();
    }

    /** A spot is valid when the block is a safe landing and the two blocks above it are passable (head/feet room). */
    private static boolean isValidStand(World world, int x, int y, int z, RtpPoolConfig config) {
        Block landing = world.getBlockAt(x, y, z);
        if (!isSafeLanding(landing, config)) return false;

        Block feet = landing.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        return isPassable(feet) && isPassable(head);
    }

    /**
     * Draws a candidate x/z. The world border is always the hard bound: no pick can ever resolve
     * outside it. The End (a single dedicated server with no border split — a small island around
     * its centre) samples a ring between the destination's min/max-radius, clamped to the border.
     * Every other destination samples uniformly from the world-border box itself, additionally
     * intersected with the portal {@link OverworldRegion} when one is configured — so a pick can
     * never land in a neighbour's half of the shared coordinate space and get bounced straight back
     * by the border enforcer. Returns {@code null} if the resulting area is empty.
     */
    private static int[] pickCandidate(World world, RegionDestination bounds, OverworldRegion region) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WorldBorder border = world.getWorldBorder();
        double half = Math.max(0, border.getSize() / 2.0 - BORDER_MARGIN);
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        if (bounds.environment() == World.Environment.THE_END) {
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

        // Start from the world-border box, then tighten to the portal region if one is configured.
        double minX = centerX - half, maxX = centerX + half;
        double minZ = centerZ - half, maxZ = centerZ + half;
        if (region != null) {
            minX = Math.max(minX, region.getMinX());
            maxX = Math.min(maxX, region.getMaxX());
            minZ = Math.max(minZ, region.getMinZ());
            maxZ = Math.min(maxZ, region.getMaxZ());
        }
        if (minX >= maxX || minZ >= maxZ) return null;
        return new int[] {
                (int) Math.floor(random.nextDouble(minX, maxX)),
                (int) Math.floor(random.nextDouble(minZ, maxZ))
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
