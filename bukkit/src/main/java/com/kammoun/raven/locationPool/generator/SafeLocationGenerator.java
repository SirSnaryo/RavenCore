package com.kammoun.raven.locationPool.generator;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationSearchBounds;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.locationPool.config.LocationPoolConfig;
import com.kammoun.raven.locationPool.protection.LocationProtection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class SafeLocationGenerator {

    private final RavenCore plugin;
    private final String serverId;
    private final LocationPoolConfig config;
    private final LocationProtection protection;

    public SafeLocationGenerator(RavenCore plugin, String serverId, LocationPoolConfig config,
                                 LocationProtection protection) {
        this.plugin = plugin;
        this.serverId = serverId;
        this.config = config;
        this.protection = protection;
    }

    public CompletableFuture<Optional<RtpLocation>> generate(LocationDimension dimension) {
        World world = findWorld(dimension);
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return attemptAsync(world, dimension, config.bounds(dimension));
    }

    private CompletableFuture<Optional<RtpLocation>> attempt(World world, LocationDimension dimension,
                                                              LocationSearchBounds bounds) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(bounds.minX(), bounds.maxX());
        int z = random.nextInt(bounds.minZ(), bounds.maxZ());
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        return world.getChunkAtAsync(chunkX, chunkZ, true)
                .handle((chunk, error) -> {
                    if (error != null || chunk == null) {
                        return CompletableFuture.completedFuture(Optional.<RtpLocation>empty());
                    }

                    CompletableFuture<Optional<RtpLocation>> inspected = new CompletableFuture<>();
                    SchedulerUtil.execute(new Location(world, x, bounds.minY(), z), () -> {
                        inspected.complete(inspectColumn(world, dimension, bounds, x, z));
                    });
                    return inspected;
                })
                .thenCompose(future -> future);
    }

    /**
     * Candidate selection must never continue directly from a Folia region thread.
     * A failed column can retry in a completely different region, so hand it back to
     * the async scheduler before requesting the next chunk.
     */
    private CompletableFuture<Optional<RtpLocation>> attemptAsync(
            World world, LocationDimension dimension, LocationSearchBounds bounds) {
        CompletableFuture<Optional<RtpLocation>> result = new CompletableFuture<>();
        SchedulerUtil.runAsync(() -> attempt(world, dimension, bounds)
                .whenComplete((location, error) -> {
                    if (error != null) result.completeExceptionally(error);
                    else result.complete(location);
                }));
        return result;
    }

    private Optional<RtpLocation> inspectColumn(World world, LocationDimension dimension,
                                                LocationSearchBounds bounds, int x, int z) {
        int highestGroundY = Math.min(bounds.maxY() - 3, world.getMaxHeight() - 3);
        int lowestGroundY = Math.max(bounds.minY(), world.getMinHeight());

        for (int groundY = highestGroundY; groundY >= lowestGroundY; groundY--) {
            Block ground = world.getBlockAt(x, groundY, z);
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);

            if (!ground.getType().isSolid() || ground.isLiquid()
                    || config.blacklistedBlocks().contains(ground.getType())) {
                continue;
            }
            if (!feet.isPassable() || feet.isLiquid() || config.blacklistedBlocks().contains(feet.getType())) {
                continue;
            }
            if (!head.isPassable() || head.isLiquid() || config.blacklistedBlocks().contains(head.getType())) {
                continue;
            }
            if (config.blacklistedBiomes().contains(feet.getBiome())) {
                continue;
            }

            int landingY = groundY + 1;
            if (!bounds.contains(x, landingY, z)) {
                continue;
            }

            Location landing = new Location(world, x + 0.5, landingY, z + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360.0f, 0.0f);
            if (protection.isProtected(landing)) {
                continue;
            }

            return Optional.of(new RtpLocation(serverId, dimension, world.getName(),
                    landing.getX(), landing.getY(), landing.getZ(), landing.getYaw(), landing.getPitch()));
        }
        return Optional.empty();
    }

    private World findWorld(LocationDimension dimension) {
        World.Environment environment = switch (dimension) {
            case OVERWORLD -> World.Environment.NORMAL;
            case NETHER -> World.Environment.NETHER;
            case END -> World.Environment.THE_END;
        };
        return plugin.getServer().getWorlds().stream()
                .filter(world -> world.getEnvironment() == environment)
                .findFirst()
                .orElse(null);
    }
}
