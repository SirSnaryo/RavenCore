package com.kammoun.raven.crates.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CrateReward(
        int slot,
        boolean giveItem,
        ItemStack displayItem,
        ItemStack rewardItem,
        List<String> commands
) {
}
