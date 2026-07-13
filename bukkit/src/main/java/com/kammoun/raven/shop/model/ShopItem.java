package com.kammoun.raven.shop.model;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

@Getter
public class ShopItem {

    private final MenuItem icon;
    private final double price;
    private final int maxStack;
    private final String purchaseTitle;

    public ShopItem(MenuItem icon, double price, int maxStack, String purchaseTitle) {
        this.icon = icon;
        this.price = price;
        this.maxStack = maxStack;
        this.purchaseTitle = purchaseTitle;
    }

    public static ShopItem fromConfig(ConfigurationSection section) {
        if (section == null) return null;
        MenuItem icon = ItemReader.readMenuItem(section);
        if (icon == null) return null;
        double price = section.getDouble("price", 0.0);
        int maxStack = section.getInt("max-stack", 64);
        String title = section.getString("purchase-inventory-title", "Purchase");
        return new ShopItem(icon, price, maxStack, title);
    }
}
