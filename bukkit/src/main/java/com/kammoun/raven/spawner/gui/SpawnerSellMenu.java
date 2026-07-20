package com.kammoun.raven.spawner.gui;

import com.kammoun.raven.library.config.KConfigLoader;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.spawner.model.SpawnerStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sell-confirmation GUI.
 * Per-player state (which spawner, total value) lives in {@link SellHolder} —
 * there are no mutable instance fields that could be shared between players.
 */
public class SpawnerSellMenu extends KConfigLoader {

    private final int size;
    private final String titleTemplate;

    private MenuItem confirm;
    private MenuItem decline;
    private MenuItem contents;
    private MenuItem filler;

    public SpawnerSellMenu(KModule<?> module) {
        super(module.getApi(), "spawner/gui/sell.yml");
        this.size          = config.getInt("Size", 27);
        this.titleTemplate = config.getString("Title", "&8Confirm Sale");
        loadItems();
    }

    private void loadItems() {
        filler   = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Filler-Item"), plugin);
        confirm  = ItemReader.readMenuItem(config.getConfigurationSection("Items.Confirm"), plugin);
        decline  = ItemReader.readMenuItem(config.getConfigurationSection("Items.Decline"), plugin);
        contents = ItemReader.readMenuItem(config.getConfigurationSection("Items.Contents"), plugin);
    }

    public void open(Player player, SpawnerStack stack, double totalValue) {
        SellHolder holder = new SellHolder(stack, totalValue);

        String title = ChatFormatter.menuTitle(PlaceholderHelper.parsePlaceholders(
                titleTemplate,
                "%type%",  stack.getType().getDisplayName(),
                "%value%", NumberUtils.formatDouble(totalValue)));

        Inventory inv = Bukkit.createInventory(holder, size, title);

        // Filler
        if (filler != null) {
            ItemStack fill = filler.getItemStack();
            for (int i = 0; i < size; i++) inv.setItem(i, fill);
        }

        // Confirm button (with value placeholder in lore)
        if (confirm != null) {
            ItemStack confirmItem = PlaceholderHelper.parsePlaceholders(
                    confirm.getItemStack(), "%value%", NumberUtils.formatDouble(totalValue));
            inv.setItem(confirm.getSlot(), confirmItem);
        }

        // Decline button
        if (decline != null) inv.setItem(decline.getSlot(), decline.getItemStack());

        // Contents summary
        if (contents != null) {
            inv.setItem(contents.getSlot(), buildContentsItem(stack));
        }

        player.openInventory(inv);
    }

    private ItemStack buildContentsItem(SpawnerStack stack) {
        // Use the first stored material as the display item icon
        Material icon = stack.getType().getDropMaterials().stream()
                .filter(m -> stack.getItemAmount(m) > 0)
                .findFirst()
                .orElse(Material.CHEST);

        ItemStack item = new ItemStack(icon, 1);
        var meta = item.getItemMeta();
        if (meta == null) return item;

        String rawName = config.getString("Items.Contents.display-name", "&#FFD447&lContents");
        meta.setDisplayName(ChatFormatter.color(rawName));

        List<String> lore = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : stack.getStoredItems().entrySet()) {
            lore.add(ChatFormatter.color("&#FFFFFF" + formatMaterial(entry.getKey()) + " &#7A7A7Ax" + entry.getValue()));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ─── Slot helpers ─────────────────────────────────────────────────────────

    public int confirmSlot() { return confirm != null ? confirm.getSlot() : -1; }
    public int declineSlot() { return decline != null ? decline.getSlot() : -1; }

    public void reload() {
        load();
        loadItems();
    }

    private static String formatMaterial(Material mat) {
        String[] words = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
