package com.kammoun.raven.spawn.afk;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.location.NetworkLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a player at the stored /setafk spot. The AFK world is taken from the stored location, so a
 * player who stands at the AFK spot and then walks away is pulled back as long as they are still in
 * that world. Leaving the world (or going to another server) releases them.
 */
public class AfkAnchorService {

    private final RavenCore api;
    private final LocationCache cache;
    private final AfkRegionChecker regionChecker;
    private final double radiusSquared;
    private final double heightSquared;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastPull = new ConcurrentHashMap<>();

    public AfkAnchorService(RavenCore api, LocationCache cache, AfkRegionChecker regionChecker,
                            int radius, int height, int cooldownSeconds) {
        this.api = api;
        this.cache = cache;
        this.regionChecker = regionChecker;
        double r = Math.max(1, radius);
        double h = Math.max(1, height);
        this.radiusSquared = r * r;
        this.heightSquared = h * h;
        this.cooldownMillis = Math.max(1, cooldownSeconds) * 1000L;
    }

    /**
     * Re-anchors the player when they leave the AFK area. Returns false when there is nothing to
     * do, which is the common case for everyone who never used /afk.
     */
    public boolean hold(Player player) {
        if (api.getStaffModeModule() != null && api.getStaffModeModule().getService() != null
                && (api.getStaffModeModule().getService().isVanished(player.getUniqueId())
                || api.getStaffModeModule().getService().isEnabled(player))) {
            return false;
        }

        Location afk = afkLocation();
        if (afk == null) return false;

        UUID uuid = player.getUniqueId();
        Location current = player.getLocation();

        if (!sameWorld(afk, current)) {
            lastPull.remove(uuid);
            return false;
        }

        if (current.getY() <= current.getWorld().getMinHeight()) {
            return pull(player, afk, true);
        }

        if (regionChecker != null && regionChecker.active() && !regionChecker.contains(player)) {
            return pull(player, afk);
        }

        if (horizontalSquared(current, afk) <= radiusSquared
                && verticalSquared(current, afk) <= heightSquared) {
            return false;
        }

        return pull(player, afk);
    }

    private boolean pull(Player player, Location afk) {
        return pull(player, afk, false);
    }

    private boolean pull(Player player, Location afk, boolean force) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = lastPull.get(uuid);
        if (!force && previous != null && now - previous < cooldownMillis) return false;

        Location target = afk.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        SchedulerUtil.execute(player, () -> player.teleportAsync(target));
        lastPull.put(uuid, now);
        return true;
    }

    public void release(Player player) {
        lastPull.remove(player.getUniqueId());
    }

    public void clear() {
        lastPull.clear();
    }

    public Location afkLocation() {
        Optional<NetworkLocation> stored = cache.get(LocationType.AFK, api.getServerId());
        if (stored.isPresent()) {
            Location local = toLocation(stored.get());
            if (local != null) return local;
        }

        for (NetworkLocation row : cache.getAll(LocationType.AFK)) {
            Location candidate = toLocation(row);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private double horizontalSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private double verticalSquared(Location a, Location b) {
        double dy = a.getY() - b.getY();
        return dy * dy;
    }

    private Location toLocation(NetworkLocation location) {
        World world = Bukkit.getWorld(location.getWorld());
        if (world == null) return null;
        return new Location(world, location.getX(), location.getY(), location.getZ());
    }

    private boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }
}
