package com.kammoun.raven.billford.gui;

import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.config.BillfordMessages;
import com.kammoun.raven.billford.manager.BillfordManager;
import com.kammoun.raven.billford.model.BillfordPreset;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin editor where required/reward items are dragged straight in. Because it needs free item
 * placement it can't extend KMenu (whose plugin-wide dispatcher cancels every click); instead it
 * runs as its own holder handled by {@link com.kammoun.raven.billford.manager.GuiManager}. Saving
 * routes through {@link BillfordManager#savePreset} so the write propagates network-wide.
 */
public class BillfordPresetEditorGui implements InventoryHolder {

    private final BillfordConfig config;
    private final BillfordMessages messages;
    private final BillfordManager manager;
    private final JavaPlugin plugin;
    private final BillfordPreset preset;

    private final int size;
    private final List<Integer> requiredSlots;
    private final int tradeSlot;
    private final int saveSlot;
    private final int infoSlot;

    private final ItemStack requiredPlaceholder;
    private final ItemStack rewardPlaceholder;

    private Inventory inventory;

    public BillfordPresetEditorGui(BillfordConfig config, BillfordMessages messages, BillfordManager manager,
                                   JavaPlugin plugin, BillfordPreset preset) {
        this.config = config;
        this.messages = messages;
        this.manager = manager;
        this.plugin = plugin;
        this.preset = preset;

        FileConfiguration cfg = config.getConfig();
        this.size = cfg.getInt("editor.size", 54);
        this.requiredSlots = cfg.getIntegerList("editor.required-items-slots");
        this.tradeSlot = cfg.getInt("editor.trade-slot", 25);
        this.saveSlot = cfg.getInt("editor.save-slot", 49);
        this.infoSlot = cfg.getInt("editor.info-slot", 4);
        this.requiredPlaceholder = read(cfg, "editor.required-placeholder", Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        this.rewardPlaceholder = read(cfg, "editor.reward-placeholder", Material.YELLOW_STAINED_GLASS_PANE);
    }

    public void open(Player player) {
        FileConfiguration cfg = config.getConfig();
        String title = ChatFormatter.color(config.getEditorTitle().replace("%preset%", preset.getName()));
        inventory = Bukkit.createInventory(this, size, title);

        ItemStack filler = read(cfg, "editor.filler", Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) {
            if (!requiredSlots.contains(i) && i != tradeSlot && i != saveSlot && i != infoSlot) {
                inventory.setItem(i, filler);
            }
        }

        inventory.setItem(infoSlot, read(cfg, "editor.info-item", Material.BOOK));
        inventory.setItem(saveSlot, read(cfg, "editor.save-item", Material.LIME_CONCRETE));

        List<ItemStack> existing = preset.getRequiredItems();
        for (int i = 0; i < requiredSlots.size(); i++) {
            ItemStack item = i < existing.size() ? existing.get(i) : requiredPlaceholder;
            inventory.setItem(requiredSlots.get(i), item);
        }
        inventory.setItem(tradeSlot, preset.getTradeItem() != null ? preset.getTradeItem() : rewardPlaceholder);

        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= size) return; // player inventory — leave it alone

        if (slot == saveSlot) {
            event.setCancelled(true);
            save((Player) event.getWhoClicked());
            return;
        }
        if (slot == infoSlot) {
            event.setCancelled(true);
            return;
        }
        if (requiredSlots.contains(slot) || slot == tradeSlot) {
            handleItemSlot(event, slot);
            return;
        }
        event.setCancelled(true);
    }

    private void handleItemSlot(InventoryClickEvent event, int slot) {
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean isPlaceholder = clicked != null
                && (clicked.isSimilar(requiredPlaceholder) || clicked.isSimilar(rewardPlaceholder));
        if (isPlaceholder && cursor != null && cursor.getType() != Material.AIR) {
            event.setCancelled(true);
            inventory.setItem(slot, cursor.clone());
            event.getWhoClicked().setItemOnCursor(null);
        }
        // Otherwise: real item in a real slot — allow normal pickup/placement.
    }

    private void save(Player player) {
        List<ItemStack> required = new ArrayList<>();
        for (int slot : requiredSlots) {
            ItemStack item = inventory.getItem(slot);
            if (isRealItem(item) && !item.isSimilar(requiredPlaceholder)) {
                required.add(item.clone());
            }
        }

        ItemStack trade = inventory.getItem(tradeSlot);
        if (trade != null && trade.isSimilar(rewardPlaceholder)) trade = null;

        if (required.isEmpty()) {
            messages.send(player, "preset.needs-required");
            return;
        }
        if (!isRealItem(trade)) {
            messages.send(player, "preset.needs-reward");
            return;
        }

        preset.setRequiredItems(required);
        preset.setTradeItem(trade.clone());

        manager.savePreset(preset, ok -> {
            if (!player.isOnline()) return;
            if (ok) {
                messages.send(player, "preset.saved", "preset", preset.getName());
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
            } else {
                messages.send(player, "preset.save-failed", "preset", preset.getName());
            }
        });
    }

    private boolean isRealItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    private ItemStack read(FileConfiguration cfg, String path, Material fallback) {
        ItemBuilder builder = ItemReader.readItem(cfg.getConfigurationSection(path), plugin);
        return builder != null ? builder.build() : new ItemBuilder(fallback).name(" ").build();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
