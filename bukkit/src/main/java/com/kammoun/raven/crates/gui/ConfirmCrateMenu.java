package com.kammoun.raven.crates.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.crates.model.CrateReward;
import com.kammoun.raven.crates.service.CrateService;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class ConfirmCrateMenu implements InventoryHolder {
    private final CrateDefinition crate;
    private final CrateReward reward;
    private final CrateService service;
    private final Inventory inventory;
    private final MenuItem accept;
    private final MenuItem deny;

    public ConfirmCrateMenu(CrateDefinition crate, CrateReward reward, CrateService service) {
        this.crate = crate;
        this.reward = reward;
        this.service = service;
        File file = new File(RavenCore.getInstance().getDataFolder(), "crates/Gui/confirm-gui.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.inventory = Bukkit.createInventory(this, config.getInt("Size", 27),
                ChatFormatter.menuTitle(config.getString("Title", "&8&lConfirm Purchase")));
        this.accept = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Accept"), RavenCore.getInstance());
        this.deny = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Deny"), RavenCore.getInstance());
        if (accept != null) inventory.setItem(accept.getSlot(), accept.getItemStack());
        if (deny != null) inventory.setItem(deny.getSlot(), deny.getItemStack());
        inventory.setItem(config.getInt("Navigation.Info.slot", 13), reward.displayItem().clone());
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (accept != null && event.getSlot() == accept.getSlot()) {
            service.openReward(player, crate, reward);
            return;
        }
        if (deny != null && event.getSlot() == deny.getSlot()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
            new CrateMenu(crate, service).open(player);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
