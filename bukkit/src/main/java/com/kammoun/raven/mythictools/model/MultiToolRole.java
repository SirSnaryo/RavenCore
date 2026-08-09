package com.kammoun.raven.mythictools.model;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Locale;

// Declaration order is the resolution order used when a block matches more than one vanilla tag.
public enum MultiToolRole {
    SWORD(Material.NETHERITE_SWORD, null),
    AXE(Material.NETHERITE_AXE, Tag.MINEABLE_AXE),
    SHOVEL(Material.NETHERITE_SHOVEL, Tag.MINEABLE_SHOVEL),
    PICKAXE(Material.NETHERITE_PICKAXE, Tag.MINEABLE_PICKAXE);

    private final Material fallbackMaterial;
    private final Tag<Material> tag;

    MultiToolRole(Material fallbackMaterial, Tag<Material> tag) {
        this.fallbackMaterial = fallbackMaterial;
        this.tag = tag;
    }

    public Material fallbackMaterial() {
        return fallbackMaterial;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean matches(Material block) {
        return tag != null && tag.isTagged(block);
    }
}
