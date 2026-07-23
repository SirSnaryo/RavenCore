package com.kammoun.raven.library.locationpool;

public record LocationSearchBounds(int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ) {

    public LocationSearchBounds {
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("Location search bounds must have a non-empty volume");
        }
    }

    public boolean fitsWithin(long safeMinX, long safeMinZ, long safeMaxX, long safeMaxZ) {
        return minX >= safeMinX && minZ >= safeMinZ && maxX <= safeMaxX && maxZ <= safeMaxZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }
}
