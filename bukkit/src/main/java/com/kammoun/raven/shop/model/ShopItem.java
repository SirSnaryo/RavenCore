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
    private final ShopCurrency currency;

    public ShopItem(MenuItem icon, double price, int maxStack, String purchaseTitle, ShopCurrency currency) {
        this.icon = icon;
        this.price = price;
        this.maxStack = maxStack;
        this.purchaseTitle = purchaseTitle;
        this.currency = currency;
    }

    public static ShopItem fromConfig(ConfigurationSection section, ShopCurrency fallbackCurrency) {
        if (section == null) return null;
        MenuItem icon = ItemReader.readMenuItem(section);
        if (icon == null) return null;
        double price = section.getDouble("price", 0.0);
        int maxStack = section.getInt("max-stack", 64);
        String title = section.getString("purchase-inventory-title", "Purchase");
        ShopCurrency currency = ShopCurrency.parse(section.getString("currency"), fallbackCurrency);
        return new ShopItem(icon, price, maxStack, title, currency);
    }
}
