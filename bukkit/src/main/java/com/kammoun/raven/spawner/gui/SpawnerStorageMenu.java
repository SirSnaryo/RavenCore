package com.kammoun.raven.spawner.gui;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.spawner.model.SpawnerStack;
import com.kammoun.raven.spawner.model.SpawnerStorageGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and opens the per-spawner-type storage GUI. Every spawner type owns its own
 * title, size and buttons (see {@link SpawnerStorageGui}) — there is no shared template
 * file used across types.
 */
public class SpawnerStorageMenu {

    private static final int CONTENT_SLOTS = 45;

    public void open(Player player, SpawnerStack stack, int page) {
        SpawnerStorageGui gui = stack.getType().getStorageGui();

        List<ItemStack> displayItems = buildDisplayItems(stack);
        int maxPages = Math.max(1, (int) Math.ceil((double) displayItems.size() / CONTENT_SLOTS));
        page = Math.max(1, Math.min(page, maxPages));

        StorageHolder holder = new StorageHolder(stack, page, maxPages);

        String title = ChatFormatter.menuTitle(PlaceholderHelper.parsePlaceholders(
                gui.getTitle(),
                "%type%",     stack.getType().getDisplayName(),
                "%amount%",   String.valueOf(stack.getStackAmount()),
                "%page%",     String.valueOf(page),
                "%max_page%", String.valueOf(maxPages)));

        Inventory inv = Bukkit.createInventory(holder, gui.getSize(), title);

        // Filler
        if (gui.getFiller() != null) {
            ItemStack fill = gui.getFiller().getItemStack();
            for (int i = 0; i < gui.getSize(); i++) inv.setItem(i, fill);
        }

        // Content slots 0–44
        int start = (page - 1) * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && (start + i) < displayItems.size(); i++) {
            inv.setItem(i, displayItems.get(start + i));
        }

        // Navigation
        if (gui.getPrevPage() != null && page > 1)        inv.setItem(gui.getPrevPage().getSlot(), gui.getPrevPage().getItemStack());
        if (gui.getNextPage() != null && page < maxPages) inv.setItem(gui.getNextPage().getSlot(), gui.getNextPage().getItemStack());
        if (gui.getDropAll()  != null)                    inv.setItem(gui.getDropAll().getSlot(),  gui.getDropAll().getItemStack());
        if (gui.getSellAll()  != null)                     inv.setItem(gui.getSellAll().getSlot(),  gui.getSellAll().getItemStack());

        // Spawner icon (with live placeholders from the GUI icon defined on the spawner type)
        if (gui.getSpawnerIcon() != null && stack.getType().getGuiIcon() != null) {
            ItemStack icon = PlaceholderHelper.parsePlaceholders(
                    stack.getType().getGuiIcon().getItemStack(),
                    "%type%",       ChatFormatter.color(stack.getType().getDisplayName()),
                    "%amount%",           String.valueOf(stack.getStackAmount()),
                    "%fill-percentage%",  String.valueOf(stack.getStoragePercent()));
            inv.setItem(gui.getSpawnerIcon().getSlot(), icon);
        }

        player.openInventory(inv);
    }

    /** Re-opens the storage at the same page (convenience for refreshing after a click). */
    public void refresh(Player player, SpawnerStack stack, int page) {
        open(player, stack, page);
    }

    // ─── Item display ─────────────────────────────────────────────────────────

    /**
     * Builds a flat ordered list of display ItemStacks for all stored items.
     * Materials follow the spawner type's drop declaration order.
     * Each material's total amount is split into stacks of ≤64 so individual clicking works.
     */
    public List<ItemStack> buildDisplayItems(SpawnerStack stack) {
        List<ItemStack> result = new ArrayList<>();
        for (Material mat : stack.getType().getDropMaterials()) {
            int total = stack.getItemAmount(mat);
            if (total <= 0) continue;
            int remaining = total;
            while (remaining > 0) {
                int amt = Math.min(remaining, 64);
                ItemStack item = new ItemStack(mat, amt);
                var meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatFormatter.color("&#FFFFFF" + formatMaterial(mat)));
                    meta.setLore(List.of(
                            ChatFormatter.color("&#7A7A7ATotal stored: &#FFFFFF" + total),
                            ChatFormatter.color("&#7A7A7AClick to collect this stack")));
                    item.setItemMeta(meta);
                }
                result.add(item);
                remaining -= amt;
            }
        }
        return result;
    }

    // ─── Util ─────────────────────────────────────────────────────────────────

    private static String formatMaterial(Material mat) {
        String[] words = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
