package com.kammoun.raven.library.border;

public final class BorderInteractionPolicy {

    private BorderInteractionPolicy() {}

    public static boolean isOutside(long minX, long minZ, long maxX, long maxZ,
                                    int blockX, int blockZ) {
        return blockX < minX || blockX >= maxX || blockZ < minZ || blockZ >= maxZ;
    }
}
