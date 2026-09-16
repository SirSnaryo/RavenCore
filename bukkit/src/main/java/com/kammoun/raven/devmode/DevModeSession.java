package com.kammoun.raven.devmode;

import org.bukkit.inventory.ItemStack;

public record DevModeSession(
        ItemStack[] hotbar
) {
}
