package com.kammoun.raven.shop.manager;

import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.shop.model.Category;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class CategoryManager {

    private final ShopModule module;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public CategoryManager(ShopModule module) {
        this.module = module;
    }

    public void load() {
        categories.clear();
        File dir = new File(module.getDataFolder(), "categories");
        if (!dir.exists()) return;

        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                Category category = Category.fromConfig(config);
                if (category != null) categories.put(key(file.getName()), category);
            } catch (Exception e) {
                module.getLogger().warning("[Shop] Failed to load " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public void reload() { load(); }

    public Category get(String name) { return categories.get(key(name)); }

    public boolean isValid(String name) { return categories.containsKey(key(name)); }

    public void open(Player player, String name) {
        Category cat = get(name);
        if (cat != null) open(player, cat);
    }

    public void open(Player player, Category category) {
        Inventory inv = category.buildInventory(module.getConfigManager()::priceLore);
        MenuItem back = category.getBackItem() != null ? category.getBackItem() : module.getConfigManager().getBackPage();
        if (back != null && back.getSlot() >= 0) {
            inv.setItem(back.getSlot(), back.getItemStack());
        }
        player.openInventory(inv);
    }

    private String key(String name) {
        return name.toLowerCase().replace(".yml", "");
    }
}
