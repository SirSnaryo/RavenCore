package com.kammoun.raven.borders.config;

import com.kammoun.raven.borders.utils.GridCell;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class BorderConfig {

    private final int cellSize;
    private final int netherRatio;
    private final int cellX;
    private final int cellZ;
    private final String cluster;
    private final String denyMessage;

    public BorderConfig(FileConfiguration root) {
        this.cellSize    = root.getInt("grid.cell-size", 5000);
        this.netherRatio = root.getInt("grid.nether-ratio", 8);
        this.cellX       = root.getInt("grid.cell-x", 0);
        this.cellZ       = root.getInt("grid.cell-z", 0);
        this.cluster     = root.getString("network.cluster", "default");
        this.denyMessage = root.getString("border.deny-message", "");
    }

    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
    public String cluster() { return cluster; }
    public String denyMessage() { return denyMessage; }

    /** Returns the cell boundary for the given dimension, or null for unsupported dimensions. */
    public GridCell cellFor(World.Environment env) {
        if (env == World.Environment.NORMAL || env == World.Environment.NETHER) {
            return GridCell.of(cellSize, netherRatio, cellX, cellZ, env);
        }
        return null;
    }
}
