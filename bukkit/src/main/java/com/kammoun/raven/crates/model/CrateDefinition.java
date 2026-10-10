package com.kammoun.raven.crates.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CrateDefinition(
        String id,
        String displayName,
        ItemStack blockItem,
        String inventoryTitle,
        int inventorySize,
        ItemStack fillerItem,
        List<CrateReward> rewards
) {
}
