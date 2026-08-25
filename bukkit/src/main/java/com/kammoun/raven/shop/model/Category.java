package com.kammoun.raven.shop.model;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.shop.model.inv.ShopCategoryHolder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Getter
public class Category {

    private final String title;
    private final int size;
    private final List<ShopItem> items;
    private final MenuItem backItem;
    private final ShopCurrency currency;

    public Category(String title, int size, List<ShopItem> items, MenuItem backItem, ShopCurrency currency) {
        this.title = title;
        this.size = (size % 9 == 0) ? size : 54;
        this.items = items;
        this.backItem = backItem;
        this.currency = currency;
    }

    public Inventory buildInventory(Function<ShopCurrency, List<String>> priceLore) {
        ShopCategoryHolder holder = new ShopCategoryHolder(this);
        Inventory inv = Bukkit.createInventory(holder, size, ChatFormatter.menuTitle(title));
        for (ShopItem item : items) {
            if (item.getIcon().getSlot() < 0) continue;
            inv.setItem(item.getIcon().getSlot(), withPriceLore(item, priceLore.apply(item.getCurrency())));
        }
        return inv;
    }

    private ItemStack withPriceLore(ShopItem shopItem, List<String> priceLore) {
        ItemStack stack = shopItem.getIcon().getItemStack().clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String formatted = shopItem.getCurrency().format(shopItem.getPrice());
        for (String line : priceLore) {
            lore.add(ChatFormatter.color(line.replace("%price%", formatted)));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public ShopItem getItemAt(ItemStack clicked) {
        if (clicked == null || clicked.getItemMeta() == null) return null;
        String clickedName = clicked.getItemMeta().getDisplayName();
        for (ShopItem item : items) {
            ItemMeta m = item.getIcon().getItemStack().getItemMeta();
            if (m != null && m.getDisplayName().equals(clickedName)
                    && item.getIcon().getItemStack().getType() == clicked.getType()) {
                return item;
            }
        }
        return null;
    }

    public static Category fromConfig(ConfigurationSection section) {
        if (section == null) return null;
        String title = section.getString("Title", "Shop");
        int size = section.getInt("Size", 27);
        ShopCurrency currency = ShopCurrency.parse(section.getString("Currency"), ShopCurrency.MONEY);
        List<ShopItem> items = new ArrayList<>();
        MenuItem backItem = null;

        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                if (key.equalsIgnoreCase("back-page")) {
                    backItem = ItemReader.readMenuItem(itemsSection.getConfigurationSection("back-page"));
                    continue;
                }
                ShopItem item = ShopItem.fromConfig(itemsSection.getConfigurationSection(key), currency);
                if (item != null) items.add(item);
            }
        }
        return new Category(title, size, items, backItem, currency);
    }
}
