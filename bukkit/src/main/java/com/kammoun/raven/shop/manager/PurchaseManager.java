package com.kammoun.raven.shop.manager;

import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.shop.model.Category;
import com.kammoun.raven.shop.model.ShopItem;
import com.kammoun.raven.shop.model.inv.PurchaseHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PurchaseManager {

    private final ShopConfigManager config;

    public PurchaseManager(ShopConfigManager config) {
        this.config = config;
    }

    public void open(Player player, ShopItem shopItem, Category category) {
        int baseAmount = Math.max(1, shopItem.getIcon().getItemStack().getAmount());
        double pricePerItem = shopItem.getPrice() / baseAmount;
        int maxStack = shopItem.getMaxStack() > 0
                ? Math.min(shopItem.getMaxStack(), shopItem.getIcon().getItemStack().getMaxStackSize())
                : shopItem.getIcon().getItemStack().getMaxStackSize();
        int initial = Math.min(baseAmount, maxStack);

        PurchaseHolder holder = new PurchaseHolder(shopItem, initial, maxStack, pricePerItem, category);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatFormatter.color(shopItem.getPurchaseTitle()));
        holder.setInventory(inv);
        refresh(inv);
        player.openInventory(inv);
    }

    public void refresh(Inventory inv) {
        if (!(inv.getHolder() instanceof PurchaseHolder h)) return;
        inv.clear();

        int cur = h.getCurrentAmount();
        int max = h.getMaxAmount();

        setButton(inv, config.getDecline());
        setButton(inv, config.getAccept());

        setButton(inv, config.getMinus64(), 64);
        if (cur > 16) setButton(inv, config.getMinus16(), 16);
        if (cur > 1)  setButton(inv, config.getMinus1(), 1);
        if (cur < max)       setButton(inv, config.getPlus1(), 1);
        if (cur + 16 <= max) setButton(inv, config.getPlus16(), 16);
        setButton(inv, config.getPlus64(), 64);

        ItemStack display = h.getShopItem().getIcon().getItemStack().clone();
        display.setAmount(Math.max(1, Math.min(cur, display.getMaxStackSize())));
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) lore.add("");
            String formatted = NumberUtils.formatDouble(h.getPricePerItem() * cur);
            for (String line : config.getPriceLore()) {
                lore.add(ChatFormatter.color(line.replace("%price%", formatted)));
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(13, display);
    }

    private void setButton(Inventory inv, MenuItem item) {
        if (item == null || item.getSlot() < 0) return;
        inv.setItem(item.getSlot(), item.getItemStack());
    }

    private void setButton(Inventory inv, MenuItem item, int visualAmount) {
        if (item == null || item.getSlot() < 0) return;
        ItemStack is = item.getItemStack().clone();
        is.setAmount(Math.max(1, Math.min(visualAmount, 64)));
        inv.setItem(item.getSlot(), is);
    }
}
