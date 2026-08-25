package com.kammoun.raven.crates.gui;

import com.kammoun.raven.crates.CratesModule;
import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.crates.model.CrateReward;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemWriter;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CrateEditorMenu implements InventoryHolder {
    private static final List<Integer> EDITABLE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final int INFO_SLOT = 4;
    private static final int SAVE_SLOT = 49;
    private static final int CANCEL_SLOT = 45;
    private static final Material PLACEHOLDER = Material.LIGHT_GRAY_STAINED_GLASS_PANE;

    private final CratesModule module;
    private final CrateDefinition crate;
    private final Player editor;
    private final File file;
    private final Inventory inventory;
    private final Map<Integer, ItemStack> slotItems = new HashMap<>();

    public CrateEditorMenu(CratesModule module, CrateDefinition crate, Player editor, File file) {
        this.module = module;
        this.crate = crate;
        this.editor = editor;
        this.file = file;
        this.inventory = Bukkit.createInventory(this, 54, ChatFormatter.menuTitle("&8Editing: &b" + crate.displayName()));
        setup();
    }

    private void setup() {
        ItemStack filler = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);
        inventory.setItem(INFO_SLOT, ItemBuilder.of(Material.BOOK).name("&6&lCrate Editor").lore(
                "&7Editing: &b" + crate.displayName(), "",
                "&eTo add an item:", "&7Pick up item from inventory", "&7and click an empty reward slot", "",
                "&eTo remove an item:", "&7Click the reward item", "",
                "&7Click &a&lSAVE &7when done!").build());
        inventory.setItem(SAVE_SLOT, ItemBuilder.of(Material.LIME_CONCRETE).name("&a&lSAVE CHANGES")
                .lore("&7Click to save all changes", "&7to this crate's rewards", "", "&eCrate: &f" + crate.displayName())
                .glow().build());
        inventory.setItem(CANCEL_SLOT, ItemBuilder.of(Material.RED_CONCRETE).name("&c&lCANCEL")
                .lore("&7Click to discard changes", "&7and close the editor").build());
        for (int slot : EDITABLE_SLOTS) inventory.setItem(slot, placeholder());
        for (CrateReward reward : crate.rewards()) {
            if (EDITABLE_SLOTS.contains(reward.slot())) {
                ItemStack item = reward.rewardItem().clone();
                inventory.setItem(reward.slot(), item);
                slotItems.put(reward.slot(), item);
            }
        }
    }

    private ItemStack placeholder() {
        return ItemBuilder.of(PLACEHOLDER).name("&7Empty Slot").lore("&8Drop an item here to add as reward").build();
    }

    public void open() {
        editor.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        if (raw >= 54) return;
        if (raw == SAVE_SLOT) {
            event.setCancelled(true);
            save();
            return;
        }
        if (raw == CANCEL_SLOT) {
            event.setCancelled(true);
            editor.sendMessage(ChatFormatter.color("&cChanges discarded."));
            editor.playSound(editor.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
            editor.closeInventory();
            return;
        }
        if (!EDITABLE_SLOTS.contains(raw)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        if (cursor != null && cursor.getType() != Material.AIR) {
            ItemStack old = slotItems.get(raw);
            ItemStack newItem = cursor.clone();
            slotItems.put(raw, newItem);
            inventory.setItem(raw, newItem);
            editor.setItemOnCursor(old == null ? null : old);
            editor.playSound(editor.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
            return;
        }
        if (clicked != null && slotItems.containsKey(raw)) {
            ItemStack removed = slotItems.remove(raw);
            inventory.setItem(raw, placeholder());
            editor.setItemOnCursor(removed);
            editor.playSound(editor.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        if (event.getRawSlots().stream().anyMatch(slot -> slot < 54)) {
            event.setCancelled(true);
        }
    }

    private void save() {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("rewards", null);
            ConfigurationSection rewards = config.createSection("rewards");
            int key = 1;
            for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
                ConfigurationSection reward = rewards.createSection(String.valueOf(key++));
                reward.set("give-icon", true);
                ConfigurationSection item = reward.createSection("item");
                item.set("slot", entry.getKey());
                ItemWriter.writeItem(item, entry.getValue());
            }
            config.save(file);
            module.reload();
            editor.sendMessage(ChatFormatter.color("&aCrate rewards saved successfully!"));
            editor.playSound(editor.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            editor.closeInventory();
        } catch (IOException e) {
            editor.sendMessage(ChatFormatter.color("&cFailed to save crate: " + e.getMessage()));
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
