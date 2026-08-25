package com.kammoun.raven.crates.gui;

import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.crates.model.CrateReward;
import com.kammoun.raven.crates.service.CrateService;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class CrateMenu implements InventoryHolder {
    private final CrateDefinition crate;
    private final CrateService service;
    private final Inventory inventory;
    private final Map<Integer, CrateReward> rewardsBySlot = new HashMap<>();

    public CrateMenu(CrateDefinition crate, CrateService service) {
        this.crate = crate;
        this.service = service;
        this.inventory = Bukkit.createInventory(this, crate.inventorySize(), ChatFormatter.menuTitle(crate.inventoryTitle()));
        setup();
    }

    private void setup() {
        if (crate.fillerItem() != null) {
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, crate.fillerItem().clone());
        }
        for (CrateReward reward : crate.rewards()) {
            if (reward.slot() < 0 || reward.slot() >= inventory.getSize()) continue;
            inventory.setItem(reward.slot(), reward.displayItem().clone());
            rewardsBySlot.put(reward.slot(), reward);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CrateReward reward = rewardsBySlot.get(event.getSlot());
        if (reward == null) return;
        new ConfirmCrateMenu(crate, reward, service).open(player);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
