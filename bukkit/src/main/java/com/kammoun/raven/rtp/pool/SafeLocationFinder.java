package com.kammoun.raven.rtp.pool;

import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import com.kammoun.raven.rtp.config.RtpPoolConfig;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
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
 */
public final class SafeLocationFinder {

    private SafeLocationFinder() {}

    /** {@code bounds} supplies the per-destination radius/Y range; {@code config} the shared attempt/unsafe-block settings. */
    public static CompletableFuture<Optional<PooledLocation>> find(World world, RegionDestination bounds, RtpPoolConfig config) {
        return attempt(world, bounds, config, config.maxAttemptsPerLocation());
    }

    private static CompletableFuture<Optional<PooledLocation>> attempt(World world, RegionDestination bounds,
                                                                        RtpPoolConfig config, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double minRadiusSq = bounds.minRadius() * bounds.minRadius();
        double maxRadiusSq = bounds.maxRadius() * bounds.maxRadius();
        double angle = random.nextDouble(0, Math.PI * 2);
        double radius = Math.sqrt(random.nextDouble() * (maxRadiusSq - minRadiusSq) + minRadiusSq);
        int x = (int) Math.floor(Math.cos(angle) * radius);
        int z = (int) Math.floor(Math.sin(angle) * radius);

        return world.getChunkAtAsync(x >> 4, z >> 4, true).thenCompose(chunk -> {
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < bounds.minY() || y > bounds.maxY()) {
                return attempt(world, bounds, config, attemptsLeft - 1);
            }

            Block landing = world.getBlockAt(x, y, z);
            if (!isSafeLanding(landing, config)) {
                return attempt(world, bounds, config, attemptsLeft - 1);
            }

            Block feet = landing.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            if (!isPassable(feet) || !isPassable(head)) {
                return attempt(world, bounds, config, attemptsLeft - 1);
            }

            float yaw = random.nextFloat() * 360f;
            return CompletableFuture.completedFuture(
                    Optional.of(new PooledLocation(world.getName(), x + 0.5, y + 1, z + 0.5, yaw)));
        });
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
