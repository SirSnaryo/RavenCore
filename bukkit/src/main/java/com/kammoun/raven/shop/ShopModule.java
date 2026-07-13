package com.kammoun.raven.shop;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.shop.command.ShopCommand;
import com.kammoun.raven.shop.listener.ShopListener;
import com.kammoun.raven.shop.manager.CategoryManager;
import com.kammoun.raven.shop.manager.PurchaseManager;
import com.kammoun.raven.shop.manager.ShopConfigManager;
import lombok.Getter;

@Getter
public class ShopModule extends KModule<RavenCore> {

    private ShopConfigManager configManager;
    private CategoryManager categoryManager;
    private PurchaseManager purchaseManager;

    public ShopModule() {
        super("shop");
    }

    @Override
    public void onModuleEnable() {
        saveDefaultConfig("categories/end-shop.yml");
        saveDefaultConfig("categories/food-shop.yml");
        saveDefaultConfig("categories/gear-shop.yml");
        saveDefaultConfig("categories/nether-shop.yml");

        configManager   = new ShopConfigManager(this);
        categoryManager = new CategoryManager(this);
        categoryManager.load();
        purchaseManager = new PurchaseManager(configManager);

        registerCommand(new ShopCommand(this), "shop");
        registerEvent(new ShopListener(this));
    }

    @Override
    public void onModuleDisable() {}

    @Override
    public void reload() {
        reloadConfig();
        configManager.load();
        categoryManager.reload();
    }

    public RavenEconomy getEconomy() {
        return api.getEconomyModule() != null ? api.getEconomyModule().getEconomy() : null;
    }
}
