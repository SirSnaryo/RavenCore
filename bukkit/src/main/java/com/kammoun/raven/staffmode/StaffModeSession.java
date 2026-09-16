package com.kammoun.raven.staffmode;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public record StaffModeSession(
        Location location,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offHand
) {
}
