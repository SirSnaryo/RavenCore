package com.kammoun.raven.mythictools.ability;

import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.model.MultiToolRole;
import com.kammoun.raven.mythictools.model.MythicToolDefinition;
import com.kammoun.raven.mythictools.model.MythicToolType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MultiToolSwitcher {

    private final MythicToolsModule module;

    public MultiToolSwitcher(MythicToolsModule module) {
        this.module = module;
    }

    public void forBlock(Player player, Material block) {
        apply(player, module.config().multiToolMaterial(module.config().multiToolRole(block)));
    }

    public void forCombat(Player player) {
        apply(player, module.config().multiToolMaterial(MultiToolRole.SWORD));
    }

    private void apply(Player player, Material material) {
        ItemStack current = player.getInventory().getItemInMainHand();
        if (material == null || current.getType() == material) return;
        player.getInventory().setItemInMainHand(current.withType(material));
        MythicToolDefinition definition = module.config().tool(MythicToolType.MULTI_TOOL);
        if (definition != null && definition.sound() != null) definition.sound().playSound(player);
    }
}
