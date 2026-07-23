package com.kammoun.raven.borders.particle;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.border.BorderWallGeometry;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BorderParticleRenderer implements Listener {

    private static final long RENDER_PERIOD_TICKS = 10L;

    private final BorderModule module;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    public BorderParticleRenderer(BorderModule module) {
        this.module = module;
    }

    public void start() {
        module.getApi().getServer().getOnlinePlayers().forEach(this::startRendering);
    }

    public void close() {
        tasks.values().forEach(ScheduledTask::cancel);
        tasks.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        startRendering(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopRendering(event.getPlayer().getUniqueId());
    }

    private void startRendering(Player player) {
        stopRendering(player.getUniqueId());
        ScheduledTask task = player.getScheduler().runAtFixedRate(
                module.getApi(), ignored -> render(player),
                () -> tasks.remove(player.getUniqueId()), 1L, RENDER_PERIOD_TICKS);
        tasks.put(player.getUniqueId(), task);
    }

    private void stopRendering(UUID playerUuid) {
        ScheduledTask task = tasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void render(Player player) {
        Location location = player.getLocation();
        BorderConfig config = module.getBorderConfig();
        GridCell cell = config.cellFor(location.getWorld().getEnvironment());
        if (cell == null) {
            return;
        }

        var segments = BorderWallGeometry.visibleSegments(
                cell.minX(), cell.minZ(), cell.maxX(), cell.maxZ(),
                location.getX(), location.getZ(), location.getY(),
                config.detectionDistance(), config.wallHeight(),
                location.getWorld().getMinHeight(), location.getWorld().getMaxHeight());

        for (BorderWallGeometry.WallSegment segment : segments) {
            renderSegment(player, config.particle(), config.particleSpacing(), segment);
        }
    }

    private void renderSegment(Player player, Particle particle, double spacing,
                               BorderWallGeometry.WallSegment segment) {
        for (double horizontal = segment.horizontalMin(); horizontal <= segment.horizontalMax(); horizontal += spacing) {
            for (double y = segment.minY(); y <= segment.maxY(); y += spacing) {
                double x = segment.axis() == BorderWallGeometry.Axis.X
                        ? segment.fixedCoordinate() : horizontal;
                double z = segment.axis() == BorderWallGeometry.Axis.Z
                        ? segment.fixedCoordinate() : horizontal;
                player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }
}
