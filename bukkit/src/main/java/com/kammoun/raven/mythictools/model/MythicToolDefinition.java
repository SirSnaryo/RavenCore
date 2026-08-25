package com.kammoun.raven.mythictools.model;

import com.kammoun.raven.library.platform.sound.KSound;
import org.bukkit.inventory.ItemStack;

public record MythicToolDefinition(
        MythicToolType type,
        ItemStack template,
        long durationMillis,
        boolean unbreakable,
        KSound sound
) {}
