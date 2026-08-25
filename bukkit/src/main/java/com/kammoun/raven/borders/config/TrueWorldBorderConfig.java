package com.kammoun.raven.borders.config;

import com.kammoun.raven.borders.utils.GridCell;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public record TrueWorldBorderConfig(boolean enabled, double centerX, double centerZ, double hugeSize,
                                    double damageAmount, double damageBuffer,
                                    int warningDistance, int warningTime,
                                    List<String> configuredEdges) {
    private static final double MAX_BORDER_SIZE = 59_999_968.0;

    public static TrueWorldBorderConfig disabled() {
        return new TrueWorldBorderConfig(false, 0.0, 0.0, MAX_BORDER_SIZE,
                0.2, 5.0, 5, 15, List.of());
    }

    public static TrueWorldBorderConfig load(FileConfiguration root, BorderConfig gridConfig) {
        if (!root.getBoolean("world-border.enabled", false)) {
            return disabled();
        }

        Double north = optionalNumber(root, "world-border.true-edges.north");
        Double south = optionalNumber(root, "world-border.true-edges.south");
        Double west = optionalNumber(root, "world-border.true-edges.west");
        Double east = optionalNumber(root, "world-border.true-edges.east");
        if (north == null && south == null && west == null && east == null) {
            throw new IllegalArgumentException("world-border.enabled is true but no true-edges are configured");
        }
        if (north != null && south != null) {
            throw new IllegalArgumentException("A server cannot configure both north and south true edges");
        }
        if (west != null && east != null) {
            throw new IllegalArgumentException("A server cannot configure both west and east true edges");
        }

        double hugeSize = requiredNonNegative(root, "world-border.huge-size", 2_000_000.0, false);
        if (hugeSize <= 0.0 || hugeSize > MAX_BORDER_SIZE) {
            throw new IllegalArgumentException("world-border.huge-size must be between 0 and "
                    + (long) MAX_BORDER_SIZE);
        }
        double damageAmount = requiredNonNegative(root, "world-border.damage-amount", 0.2, true);
        double damageBuffer = requiredNonNegative(root, "world-border.damage-buffer", 5.0, true);
        int warningDistance = requiredNonNegativeInt(root, "world-border.warning-distance", 5);
        int warningTime = requiredNonNegativeInt(root, "world-border.warning-time", 15);

        GridCell cell = gridConfig.cellFor(World.Environment.NORMAL);
        validateCellEdge("west", west, cell.minX());
        validateCellEdge("east", east, cell.maxX());
        validateCellEdge("north", north, cell.minZ());
        validateCellEdge("south", south, cell.maxZ());

        double halfSize = hugeSize / 2.0;
        double centerX = west != null ? west + halfSize
                : east != null ? east - halfSize
                : (cell.minX() + cell.maxX()) / 2.0;
        double centerZ = north != null ? north + halfSize
                : south != null ? south - halfSize
                : (cell.minZ() + cell.maxZ()) / 2.0;

        List<String> edges = new ArrayList<>(2);
        if (north != null) edges.add("north=" + format(north));
        if (south != null) edges.add("south=" + format(south));
        if (west != null) edges.add("west=" + format(west));
        if (east != null) edges.add("east=" + format(east));
        return new TrueWorldBorderConfig(true, centerX, centerZ, hugeSize,
                damageAmount, damageBuffer, warningDistance, warningTime, List.copyOf(edges));
    }

    private static Double optionalNumber(FileConfiguration root, String path) {
        if (!root.isSet(path)) return null;
        Object raw = root.get(path);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        return value;
    }

    private static double requiredNonNegative(FileConfiguration root, String path,
                                              double fallback, boolean allowZero) {
        Object raw = root.isSet(path) ? root.get(path) : fallback;
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        double value = number.doubleValue();
        if (value < 0.0 || !allowZero && value == 0.0) {
            throw new IllegalArgumentException(path + " must be " + (allowZero ? "zero or greater" : "greater than zero"));
        }
        return value;
    }

    private static int requiredNonNegativeInt(FileConfiguration root, String path, int fallback) {
        Object raw = root.isSet(path) ? root.get(path) : fallback;
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a non-negative integer");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " must be a non-negative integer");
        }
        return (int) value;
    }

    private static void validateCellEdge(String direction, Double configured, long cellEdge) {
        if (configured != null && Double.compare(configured, (double) cellEdge) != 0) {
            throw new IllegalArgumentException("world-border.true-edges." + direction + "="
                    + format(configured) + " does not match this server cell edge " + cellEdge);
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
