package com.kammoun.raven.library.border;

import java.util.ArrayList;
import java.util.List;

public final class BorderWallGeometry {

    private BorderWallGeometry() {}

    public static List<WallSegment> visibleSegments(
            long minX, long minZ, long maxX, long maxZ,
            double playerX, double playerZ, double playerY,
            double detectionDistance, double wallHeight,
            double worldMinY, double worldMaxY) {
        if (detectionDistance <= 0 || wallHeight <= 0 || worldMaxY <= worldMinY) {
            return List.of();
        }

        double height = Math.min(wallHeight, worldMaxY - worldMinY);
        double minY = playerY - height / 2.0;
        double maxY = minY + height;
        if (minY < worldMinY) {
            minY = worldMinY;
            maxY = minY + height;
        }
        if (maxY > worldMaxY) {
            maxY = worldMaxY;
            minY = maxY - height;
        }

        List<WallSegment> segments = new ArrayList<>(4);
        if (Math.abs(playerX - minX) <= detectionDistance) {
            segments.add(new WallSegment(Axis.X, minX,
                    Math.max(minZ, playerZ - detectionDistance),
                    Math.min(maxZ, playerZ + detectionDistance), minY, maxY));
        }
        if (Math.abs(playerX - maxX) <= detectionDistance) {
            segments.add(new WallSegment(Axis.X, maxX,
                    Math.max(minZ, playerZ - detectionDistance),
                    Math.min(maxZ, playerZ + detectionDistance), minY, maxY));
        }
        if (Math.abs(playerZ - minZ) <= detectionDistance) {
            segments.add(new WallSegment(Axis.Z, minZ,
                    Math.max(minX, playerX - detectionDistance),
                    Math.min(maxX, playerX + detectionDistance), minY, maxY));
        }
        if (Math.abs(playerZ - maxZ) <= detectionDistance) {
            segments.add(new WallSegment(Axis.Z, maxZ,
                    Math.max(minX, playerX - detectionDistance),
                    Math.min(maxX, playerX + detectionDistance), minY, maxY));
        }
        return List.copyOf(segments);
    }

    public enum Axis {
        X, Z
    }

    public record WallSegment(Axis axis, double fixedCoordinate,
                              double horizontalMin, double horizontalMax,
                              double minY, double maxY) {}
}
