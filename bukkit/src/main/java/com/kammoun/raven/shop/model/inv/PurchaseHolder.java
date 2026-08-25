package com.kammoun.raven.shop.model.inv;

import com.kammoun.raven.shop.model.Category;
import com.kammoun.raven.shop.model.ShopItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

@Getter
public class PurchaseHolder implements InventoryHolder {

    private final ShopItem shopItem;
    @Setter private int currentAmount;
    private final int maxAmount;
    private final double pricePerItem;
    private final Category category;
    @Setter private Inventory inventory;
    private boolean processing;

    public PurchaseHolder(ShopItem shopItem, int currentAmount, int maxAmount, double pricePerItem, Category category) {
        this.shopItem = shopItem;
        this.currentAmount = currentAmount;
        this.maxAmount = maxAmount;
        this.pricePerItem = pricePerItem;
        this.category = category;
    }

    public boolean beginPurchase() {
        if (processing) return false;
        processing = true;
        return true;
    }

    public void endPurchase() {
        processing = false;
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
