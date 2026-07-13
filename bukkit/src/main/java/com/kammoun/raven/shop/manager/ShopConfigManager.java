package com.kammoun.raven.shop.manager;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.shop.model.inv.MainShopHolder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ShopConfigManager {

    private final ShopModule module;

    private String mainTitle;
    private int mainSize;
    private MenuItem backPage, decline, accept;
    private MenuItem minus64, minus16, minus1, plus1, plus16, plus64;

    private final Map<String, MainCategoryItem> categoryItems = new LinkedHashMap<>();
    private List<String> priceLore;
    private String msgNotEnoughBalance;
    private String msgNotEnoughSpace;
    private String msgBuying;

    public ShopConfigManager(ShopModule module) {
        this.module = module;
        load();
    }

    public void load() {
        categoryItems.clear();
        var config = module.getConfig();

        priceLore = config.getStringList("Price-Lore");
        if (priceLore.isEmpty()) priceLore = List.of("&fBuy price: &a$%price%");

        ConfigurationSection main = config.getConfigurationSection("main-inventory");
        if (main == null) {
            module.getLogger().warning("[Shop] Missing 'main-inventory' in config.yml");
            return;
        }

        mainTitle = ChatFormatter.color(main.getString("Title", "Shop"));
        mainSize  = main.getInt("Size", 27);

        ConfigurationSection items = main.getConfigurationSection("items");
        if (items != null) {
            accept   = ItemReader.readMenuItem(items.getConfigurationSection("accept"));
            decline  = ItemReader.readMenuItem(items.getConfigurationSection("decline"));
            backPage = ItemReader.readMenuItem(items.getConfigurationSection("back-page"));
        }

        ConfigurationSection buttons = config.getConfigurationSection("purchase-inventory.buttons");
        if (buttons != null) {
            minus64 = ItemReader.readMenuItem(buttons.getConfigurationSection("minus-64"));
            minus16 = ItemReader.readMenuItem(buttons.getConfigurationSection("minus-16"));
            minus1  = ItemReader.readMenuItem(buttons.getConfigurationSection("minus-1"));
            plus1   = ItemReader.readMenuItem(buttons.getConfigurationSection("plus-1"));
            plus16  = ItemReader.readMenuItem(buttons.getConfigurationSection("plus-16"));
            plus64  = ItemReader.readMenuItem(buttons.getConfigurationSection("plus-64"));
        }

        ConfigurationSection categories = main.getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                ConfigurationSection cs = categories.getConfigurationSection(key);
                if (cs == null) continue;
                MenuItem icon = ItemReader.readMenuItem(cs);
                if (icon != null) {
                    categoryItems.put(key, new MainCategoryItem(icon, cs.getString("category")));
                }
            }
        }

        ConfigurationSection msgs = config.getConfigurationSection("messages");
        if (msgs != null) {
            msgNotEnoughBalance = msgs.getString("not-enough-balance", "&cYou don't have enough money.");
            msgNotEnoughSpace   = msgs.getString("not-enough-space", "&cNot enough inventory space.");
            msgBuying           = msgs.getString("buying", "&aYou bought %amount% %item-name%.");
        }
    }

    public void openMainMenu(Player player) {
        MainShopHolder holder = new MainShopHolder();
        Inventory inv = Bukkit.createInventory(holder, mainSize, mainTitle);
        holder.setInventory(inv);
        for (MainCategoryItem ci : categoryItems.values()) {
            int slot = ci.getIcon().getSlot();
            if (slot >= 0 && slot < mainSize) {
                inv.setItem(slot, ci.getIcon().getItemStack());
            }
        }
        player.openInventory(inv);
    }

    @Getter
    public static class MainCategoryItem {
        private final MenuItem icon;
        private final String categoryFile; // null = command-only

        public MainCategoryItem(MenuItem icon, String categoryFile) {
            this.icon = icon;
            this.categoryFile = categoryFile;
        }
    }
}
