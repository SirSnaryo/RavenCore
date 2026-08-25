package com.kammoun.raven.shop.utils;

import com.kammoun.raven.library.sell.SellBridgeInterface;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.shop.model.Category;
import com.kammoun.raven.shop.model.ShopCurrency;
import com.kammoun.raven.shop.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopSellPriceGuard {

    private static final double PRICE_EPSILON = 0.000_001D;

    private final ShopModule module;
    private volatile Map<Material, MaterialPrices> prices = Map.of();
    private final Set<Material> runtimeWarnings = ConcurrentHashMap.newKeySet();

    public ShopSellPriceGuard(ShopModule module) {
        this.module = module;
    }

    public void reloadAndWarn() {
        runtimeWarnings.clear();
        if (module.getApi().getShopModule() == null
                || module.getApi().getShopModule().getCategoryManager() == null) {
            prices = Map.of();
            module.getLogger().warning("[Shop] Shop price protection could not load because Shop is unavailable.");
            return;
        }

        Map<Material, MutableMaterialPrices> building = new EnumMap<>(Material.class);
        int warnings = 0;
        for (Map.Entry<String, Category> categoryEntry
                : module.getApi().getShopModule().getCategoryManager().getCategories().entrySet()) {
            for (ShopItem shopItem : categoryEntry.getValue().getItems()) {
                if (shopItem.getCurrency() != ShopCurrency.MONEY) continue;
                ItemStack template = shopItem.getIcon().getItemStack().clone();
                int configuredAmount = Math.max(1, template.getAmount());
                double purchasePerItem = shopItem.getPrice() / configuredAmount;
                if (!Double.isFinite(purchasePerItem) || purchasePerItem < 0D) continue;
                template.setAmount(1);

                building.computeIfAbsent(template.getType(), ignored -> new MutableMaterialPrices())
                        .add(template, purchasePerItem);

                double ravenSellPrice = module.getShopPriceGuard().price(template);
                if (ravenSellPrice > purchasePerItem + PRICE_EPSILON) {
                    warnings++;
                    module.getLogger().warning(String.format(Locale.US,
                            "[Shop] Unsafe buy/sell price for %s in shop category '%s': "
                                    + "shop buy $%.2f each, RavenSell base $%.2f each. "
                                    + "Sell Axe payout will be capped at $%.2f each.",
                            template.getType(), categoryEntry.getKey(), purchasePerItem,
                            ravenSellPrice, purchasePerItem));
                }
            }
        }

        Map<Material, MaterialPrices> ready = new EnumMap<>(Material.class);
        building.forEach((material, value) -> ready.put(material, value.freeze()));
        prices = Map.copyOf(ready);
        module.getLogger().info("[Shop] Shop sell-price protection indexed "
                + ready.size() + " materials; unsafe base-price warnings: " + warnings + ".");
    }

    public double maximumEarnings(UUID playerId, ItemStack item, int amount) {
        MaterialPrices materialPrices = prices.get(item.getType());
        if (materialPrices == null) return Double.MAX_VALUE;

        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        double perItem = materialPrices.minimumPurchasePrice();
        for (PriceEntry entry : materialPrices.exactPrices()) {
            if (entry.template().isSimilar(normalized)) {
                perItem = Math.min(perItem, entry.purchasePerItem());
            }
        }
        double maximum = Math.max(0D, perItem * Math.max(0, amount));
        double effective = effectivePrice(playerId, item) * Math.max(0, amount);
        if (effective > maximum + PRICE_EPSILON && runtimeWarnings.add(item.getType())) {
            module.getLogger().warning(String.format(Locale.US,
                    "[Shop] RavenSell multiplier pushed %s above its shop purchase price: "
                            + "uncapped $%.2f, capped $%.2f for %d item(s). Further warnings for this "
                            + "material are suppressed until reload.",
                    item.getType(), effective, maximum, amount));
        }
        return maximum;
    }

    private SellBridgeInterface sellBridge() {
        if (module.getApi().getMythicToolsModule() == null) return SellBridgeInterface.unavailable();
        return module.getApi().getMythicToolsModule().sellBridge();
    }

    public double price(ItemStack item) {
        return sellBridge().price(item);
    }

    public double effectivePrice(java.util.UUID playerId, ItemStack item) {
        return sellBridge().effectivePrice(playerId, item);
    }

    private record PriceEntry(ItemStack template, double purchasePerItem) {}

    private record MaterialPrices(List<PriceEntry> exactPrices, double minimumPurchasePrice) {}

    private static final class MutableMaterialPrices {
        private final List<PriceEntry> exact = new ArrayList<>();
        private double minimum = Double.MAX_VALUE;

        private void add(ItemStack template, double price) {
            exact.add(new PriceEntry(template, price));
            minimum = Math.min(minimum, price);
        }

        private MaterialPrices freeze() {
            return new MaterialPrices(List.copyOf(exact), minimum);
        }
    }
}
