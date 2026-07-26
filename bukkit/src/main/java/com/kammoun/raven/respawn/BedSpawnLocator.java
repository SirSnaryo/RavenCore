package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class BedSpawnLocator {
    private BedSpawnLocator() {
    }

    public static Optional<Block> canonicalBed(Block block) {
        if (!Tag.BEDS.isTagged(block.getType()) || !(block.getBlockData() instanceof Bed bed)) {
            return Optional.empty();
        }
        Block head = bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing());
        if (!(head.getBlockData() instanceof Bed headData) || headData.getPart() != Bed.Part.HEAD) {
            return Optional.empty();
        }
        Block foot = head.getRelative(headData.getFacing().getOppositeFace());
        if (!(foot.getBlockData() instanceof Bed footData) || footData.getPart() != Bed.Part.FOOT) {
            return Optional.empty();
        }
        return Optional.of(head);
    }

    public static void resolveAsync(RavenCore plugin, BedSpawnRecord record,
                                    Consumer<BedSpawnResolution> callback) {
        World world = plugin.getServer().getWorld(record.world());
        if (world == null) {
            callback.accept(BedSpawnResolution.unverifiable());
            return;
        }

        Location anchor = new Location(world, record.x(), record.y(), record.z());
        world.getChunkAtAsync(record.x() >> 4, record.z() >> 4).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                callback.accept(BedSpawnResolution.unverifiable());
                return;
            }
            SchedulerUtil.execute(anchor, () -> {
                try {
                    callback.accept(inspect(record, world));
                } catch (RuntimeException errorDuringInspection) {
                    plugin.getLogger().warning("[Respawn] Could not verify bed at "
                            + record.serverId() + "/" + record.world() + " "
                            + record.x() + "," + record.y() + "," + record.z() + ": "
                            + errorDuringInspection.getMessage());
                    callback.accept(BedSpawnResolution.unverifiable());
                }
            });
        });
    }

    private static BedSpawnResolution inspect(BedSpawnRecord record, World world) {
        Block stored = world.getBlockAt(record.x(), record.y(), record.z());
        Optional<Block> canonical = canonicalBed(stored);
        if (canonical.isEmpty()) return BedSpawnResolution.invalid();

        Block head = canonical.get();
        Bed data = (Bed) head.getBlockData();
        Block foot = head.getRelative(data.getFacing().getOppositeFace());
        List<Block> candidates = new ArrayList<>();
        addNeighbors(candidates, head);
        addNeighbors(candidates, foot);
        for (Block feet : candidates) {
            if (isSafe(feet)) {
                return BedSpawnResolution.valid(new Location(world, feet.getX() + 0.5, feet.getY(),
                        feet.getZ() + 0.5, 0.0f, 0.0f));
            }
        }
        return BedSpawnResolution.invalid();
    }

    private static void addNeighbors(List<Block> candidates, Block bed) {
        candidates.add(bed.getRelative(1, 0, 0));
        candidates.add(bed.getRelative(-1, 0, 0));
        candidates.add(bed.getRelative(0, 0, 1));
        candidates.add(bed.getRelative(0, 0, -1));
        candidates.add(bed.getRelative(1, 0, 1));
        candidates.add(bed.getRelative(1, 0, -1));
        candidates.add(bed.getRelative(-1, 0, 1));
        candidates.add(bed.getRelative(-1, 0, -1));
    }

    private static boolean isSafe(Block feet) {
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        if (!feet.isPassable() || !head.isPassable() || !floor.getType().isSolid()) return false;
        Material floorType = floor.getType();
        return floorType != Material.MAGMA_BLOCK && floorType != Material.CACTUS
                && floorType != Material.CAMPFIRE && floorType != Material.SOUL_CAMPFIRE;
    }

    public enum BedSpawnStatus {
        VALID,
        INVALID,
        UNVERIFIABLE
    }

    public record BedSpawnResolution(BedSpawnStatus status, Location location) {
        public static BedSpawnResolution valid(Location location) {
            return new BedSpawnResolution(BedSpawnStatus.VALID, location);
        }

        public static BedSpawnResolution invalid() {
            return new BedSpawnResolution(BedSpawnStatus.INVALID, null);
        }

        public static BedSpawnResolution unverifiable() {
            return new BedSpawnResolution(BedSpawnStatus.UNVERIFIABLE, null);
        }
    }
}
