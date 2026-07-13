package com.kammoun.raven.spawner.model;

import lombok.Getter;
import org.bukkit.Material;

@Getter
public class SpawnerDrop {

    private final Material material;
    private final int amountPerSpawner;
    private final double sellPrice;

    public SpawnerDrop(Material material, int amountPerSpawner, double sellPrice) {
        this.material = material;
        this.amountPerSpawner = amountPerSpawner;
        this.sellPrice = sellPrice;
    }
}
