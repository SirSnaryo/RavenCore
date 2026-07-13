package com.kammoun.raven.shop.model.inv;

import com.kammoun.raven.shop.model.Category;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

@Getter
public class ShopCategoryHolder implements InventoryHolder {
    private final Category category;
    private Inventory inventory;

    public ShopCategoryHolder(Category category) {
        this.category = category;
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
