package com.kammoun.raven.library.sell;

import com.kammoun.raven.api.RavenSellProvider;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class RavenSellBridge implements SellBridgeInterface {

    @Override
    public boolean available() {
        return RavenSellProvider.isLoaded();
    }

    @Override
    public boolean sellable(ItemStack item) {
        return available() && RavenSellProvider.get().isSellable(item);
    }

    @Override
    public double price(ItemStack item) {
        return available() ? RavenSellProvider.get().getPrice(item) : -1D;
    }

    @Override
    public double effectivePrice(UUID playerId, ItemStack item) {
        if (!available()) return -1D;
        double base = RavenSellProvider.get().getPrice(item);
        com.kammoun.raven.api.SellCategory category = RavenSellProvider.get().getCategory(item);
        double multiplier = category == null ? 1D : RavenSellProvider.get().getMultiplier(playerId, category);
        return base * multiplier;
    }

    @Override
    public double record(UUID playerId, ItemStack item, int amount, double maximumEarnings) {
        return available()
                ? RavenSellProvider.get().recordSellCapped(playerId, item, amount, maximumEarnings)
                : -1D;
    }
}
