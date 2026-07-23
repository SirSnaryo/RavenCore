package com.kammoun.raven.respawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public static Optional<Location> safeSpawn(BedSpawnRecord record) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(record.world());
        if (world == null) return Optional.empty();
        Block stored = world.getBlockAt(record.x(), record.y(), record.z());
        Optional<Block> canonical = canonicalBed(stored);
        if (canonical.isEmpty()) return Optional.empty();

        Block head = canonical.get();
        Bed data = (Bed) head.getBlockData();
        Block foot = head.getRelative(data.getFacing().getOppositeFace());
        List<Block> candidates = new ArrayList<>();
        addNeighbors(candidates, head);
        addNeighbors(candidates, foot);
        for (Block feet : candidates) {
            if (isSafe(feet)) {
                return Optional.of(new Location(world, feet.getX() + 0.5, feet.getY(),
                        feet.getZ() + 0.5, 0.0f, 0.0f));
            }
        }
        return Optional.empty();
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
}
