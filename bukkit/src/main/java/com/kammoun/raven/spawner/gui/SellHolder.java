package com.kammoun.raven.spawner.gui;

import com.kammoun.raven.spawner.model.SpawnerStack;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

@Getter
public class SellHolder implements InventoryHolder {

    private final SpawnerStack stack;
    private final double totalValue;

    public SellHolder(SpawnerStack stack, double totalValue) {
        this.stack      = stack;
        this.totalValue = totalValue;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("SellHolder is not a direct inventory provider");
    }
}
