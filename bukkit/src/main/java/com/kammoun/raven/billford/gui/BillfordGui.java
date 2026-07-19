package com.kammoun.raven.billford.gui;

import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.config.BillfordMessages;
import com.kammoun.raven.billford.model.BillfordPreset;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
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
 * The player-facing trade view. Read-only (every click is cancelled); confirming the trade
 * consumes the preset's required items and hands back the reward. Layout and decorative items
 * come from the module's config.yml {@code gui} section.
 */
public class BillfordGui implements InventoryHolder {

    private final BillfordConfig config;
    private final BillfordMessages messages;
    private final JavaPlugin plugin;

    private Inventory inventory;
    private BillfordPreset preset;

    public BillfordGui(BillfordConfig config, BillfordMessages messages, JavaPlugin plugin) {
        this.config = config;
        this.messages = messages;
        this.plugin = plugin;
    }

    public void open(Player player, BillfordPreset preset) {
        if (preset == null || !preset.isValid()) {
            messages.send(player, "no-preset");
            return;
        }
        this.preset = preset;

        String title = ChatFormatter.color(config.getGuiTitle());
        inventory = Bukkit.createInventory(this, config.getGuiSize(), title);

        FileConfiguration cfg = config.getConfig();

        List<Integer> requiredSlots = config.getRequiredSlots();
        List<ItemStack> requiredItems = preset.getRequiredItems();
        for (int i = 0; i < requiredSlots.size() && i < requiredItems.size(); i++) {
            inventory.setItem(requiredSlots.get(i), requiredItems.get(i));
        }

        inventory.setItem(config.getTradeSlot(), preset.getTradeItem());

        MenuItem confirm = ItemReader.readMenuItem(cfg.getConfigurationSection("gui.confirm-item"), plugin);
        if (confirm != null && confirm.getSlot() >= 0) {
            inventory.setItem(confirm.getSlot(), confirm.getItemStack());
        }

        MenuItem info = ItemReader.readMenuItem(cfg.getConfigurationSection("gui.info-item"), plugin);
        if (info != null && info.getSlot() >= 0) {
            inventory.setItem(info.getSlot(), buildInfoItem(cfg.getConfigurationSection("gui.info-item")));
        }

        applyFiller(cfg);
        player.openInventory(inventory);
    }

    private void applyFiller(FileConfiguration cfg) {
        ItemBuilder builder = ItemReader.readItem(cfg.getConfigurationSection("gui.filler.item"), plugin);
        if (builder == null) return;
        ItemStack filler = builder.build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler);
        }
    }

    /** Reads the info item and expands the {@code %required_item%}/{@code %item%} lore tokens. */
    private ItemStack buildInfoItem(ConfigurationSection section) {
        ItemBuilder builder = ItemReader.readItem(section, plugin);
        ItemStack item = builder != null ? builder.build() : new ItemStack(Material.BOOK);

        if (!section.contains("lore")) return item;

        List<String> out = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            if (line.contains("%required_item%")) {
                out.addAll(describeRequired());
            } else {
                out.add(ChatFormatter.color(line.replace("%item%", describeItem(preset.getTradeItem()))));
            }
        }
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(out);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> describeRequired() {
        List<String> lines = new ArrayList<>();
        for (ItemStack item : preset.getRequiredItems()) {
            if (item == null) continue;
            lines.add(ChatFormatter.color("&#7A7A7A• &#FFFFFF" + item.getAmount() + "x " + displayName(item)));
        }
        return lines;
    }

    private String describeItem(ItemStack item) {
        return item.getAmount() + "x " + displayName(item);
    }

    private String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return prettyMaterial(item.getType());
    }

    private String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        MenuItem confirm = ItemReader.readMenuItem(config.getConfig().getConfigurationSection("gui.confirm-item"), plugin);
        if (confirm != null && event.getSlot() == confirm.getSlot()) {
            handleTradeConfirm(player);
        }
    }

    private void handleTradeConfirm(Player player) {
        if (preset == null || !preset.isValid()) {
            messages.send(player, "no-preset");
            player.closeInventory();
            return;
        }
        if (!hasRequiredItems(player)) {
            messages.send(player, "missing-items");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            messages.send(player, "inventory-full");
            return;
        }

        for (ItemStack required : preset.getRequiredItems()) {
            removeItems(player, required);
        }
        player.getInventory().addItem(preset.getTradeItem().clone());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        messages.send(player, "trade-success");
        player.closeInventory();
    }

    private boolean hasRequiredItems(Player player) {
        for (ItemStack required : preset.getRequiredItems()) {
            if (!hasItem(player, required)) return false;
        }
        return true;
    }

    private boolean hasItem(Player player, ItemStack item) {
        int needed = item.getAmount();
        int found = 0;
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && invItem.isSimilar(item)) {
                found += invItem.getAmount();
                if (found >= needed) return true;
            }
        }
        return false;
    }

    private void removeItems(Player player, ItemStack item) {
        int remaining = item.getAmount();
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack invItem = player.getInventory().getItem(i);
            if (invItem == null || !invItem.isSimilar(item)) continue;
            if (invItem.getAmount() > remaining) {
                invItem.setAmount(invItem.getAmount() - remaining);
                return;
            }
            remaining -= invItem.getAmount();
            player.getInventory().setItem(i, null);
            if (remaining <= 0) return;
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
