package com.kammoun.raven.library.sell;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface SellBridgeInterface {

    boolean available();

    boolean sellable(ItemStack item);

    double price(ItemStack item);

    double effectivePrice(UUID playerId, ItemStack item);

    double record(UUID playerId, ItemStack item, int amount, double maximumEarnings);

    static SellBridgeInterface unavailable() {
        return new SellBridgeInterface() {
            @Override public boolean available() { return false; }
            @Override public boolean sellable(ItemStack item) { return false; }
            @Override public double price(ItemStack item) { return -1D; }
            @Override public double effectivePrice(UUID playerId, ItemStack item) { return -1D; }
            @Override public double record(UUID playerId, ItemStack item, int amount, double maximumEarnings) {
                return -1D;
            }
        };
    }
}
