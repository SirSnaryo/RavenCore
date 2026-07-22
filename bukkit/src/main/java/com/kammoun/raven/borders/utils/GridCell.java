package com.kammoun.raven.borders.utils;

import org.bukkit.World;

/**
 * Immutable boundary box for one server cell in one dimension.
 * All coordinates are inclusive min / exclusive max.
 */
public record GridCell(long minX, long minZ, long maxX, long maxZ) {

    /**
     * Build the cell for the given dimension from raw config values.
     *
     * @param cellSize    overworld blocks per cell edge
     * @param netherRatio overworld:nether ratio (vanilla = 8)
     * @param cellX       this server's cell column
     * @param cellZ       this server's cell row
     * @param env         dimension to compute for
     */
    public static GridCell of(int cellSize, int netherRatio, int cellX, int cellZ, World.Environment env) {
        long size = env == World.Environment.NETHER ? (long) cellSize / netherRatio : cellSize;
        long minX = (long) cellX * size;
        long minZ = (long) cellZ * size;
        return new GridCell(minX, minZ, minX + size, minZ + size);
    }

    public boolean contains(double x, double z) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    /** Returns the direction the coordinate exited, or null if still inside. */
    public Direction exitDirection(double x, double z) {
        if (x < minX) return Direction.WEST;
        if (x >= maxX) return Direction.EAST;
        if (z < minZ) return Direction.NORTH;
        if (z >= maxZ) return Direction.SOUTH;
        return null;
    }

    public enum Direction {
        NORTH(0, -1), SOUTH(0, 1), EAST(1, 0), WEST(-1, 0);

        public final int dx, dz;

        Direction(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }
}
