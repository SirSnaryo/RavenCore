package com.kammoun.raven.spawner.gui;

import com.kammoun.raven.spawner.model.SpawnerStack;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

@Getter
public class StorageHolder implements InventoryHolder {

    private final SpawnerStack stack;
    private final int page;
    private final int maxPages;

    public StorageHolder(SpawnerStack stack, int page, int maxPages) {
        this.stack    = stack;
        this.page     = page;
        this.maxPages = maxPages;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("StorageHolder is not a direct inventory provider");
    }
}
