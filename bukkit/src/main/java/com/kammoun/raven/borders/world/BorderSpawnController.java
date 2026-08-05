package com.kammoun.raven.borders.world;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.logging.Logger;

public final class BorderSpawnController {

    private final RavenCore plugin;
    private final Logger logger;
    private BorderConfig config;

    public BorderSpawnController(RavenCore plugin, Logger logger, BorderConfig config) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
    }

    public void reload(BorderConfig config) {
        this.config = config;
        applyLoadedWorlds();
    }

    public void applyLoadedWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            apply(world);
        }
    }

    public void apply(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        GridCell cell = config.cellFor(World.Environment.NORMAL);
        if (cell == null) {
            return;
        }

        int x = center(cell.minX(), cell.maxX());
        int z = center(cell.minZ(), cell.maxZ());
        Location center = new Location(world, x, 0.0D, z);

        SchedulerUtil.execute(center, () -> {
            int y = safeSpawnY(world, x, z);
            world.setSpawnLocation(x, y, z);
            logger.info("[Borders] Set " + world.getName() + " default spawn to cell center "
                    + x + ", " + y + ", " + z + ".");
        });
    }

    private static int center(long min, long max) {
        return Math.toIntExact(Math.floorDiv(min + max, 2L));
    }

    private static int safeSpawnY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z) + 1;
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight() - 2;
        if (y < min) return min;
        if (y > max) return max;
        return y;
    }
}
