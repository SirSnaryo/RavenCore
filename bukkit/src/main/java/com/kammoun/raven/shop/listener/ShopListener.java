package com.kammoun.raven.shop.listener;

import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.item.ClickAction;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.util.CommandHelper;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.shop.model.Category;
import com.kammoun.raven.shop.model.ShopItem;
import com.kammoun.raven.shop.model.inv.MainShopHolder;
import com.kammoun.raven.shop.model.inv.PurchaseHolder;
import com.kammoun.raven.shop.model.inv.ShopCategoryHolder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class ShopListener implements Listener {

    private final ShopModule module;

    public ShopListener(ShopModule module) {
        this.module = module;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        if (!isShopHolder(holder)) return;

        if (event.getClickedInventory() != topInventory) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        if (isHoldingItem(event) || event.getHotbarButton() >= 0
                || event.getClick().isKeyboardClick() || event.getClick().isCreativeAction()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (holder instanceof MainShopHolder) {
            click(player);
            handleMain(player, clicked);
        } else if (holder instanceof ShopCategoryHolder catHolder) {
            click(player);
            handleCategory(player, clicked, catHolder.getCategory());
        } else if (holder instanceof PurchaseHolder purchaseHolder) {
            click(player);
            handlePurchase(player, event.getSlot(), clicked, purchaseHolder, topInventory);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isShopHolder(holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesShop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesShop) {
            event.setCancelled(true);
        }
    }

    private boolean isShopHolder(InventoryHolder holder) {
        return holder instanceof MainShopHolder
                || holder instanceof ShopCategoryHolder
                || holder instanceof PurchaseHolder;
    }

    private boolean isHoldingItem(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        return cursor != null && !cursor.getType().isAir();
    }

    private void handleMain(Player player, ItemStack clicked) {
        for (var ci : module.getConfigManager().getCategoryItems().values()) {
            if (clicked.isSimilar(ci.getIcon().getItemStack())) {
                if (ci.getCategoryFile() != null) {
                    module.getCategoryManager().open(player, ci.getCategoryFile());
                } else {
                    ClickAction action = ci.getIcon().getAction();
                    if (action != null) CommandHelper.executeCommand(player, action.commands());
                }
                return;
            }
        }
    }

    private void handleCategory(Player player, ItemStack clicked, Category category) {
        var back = category.getBackItem() != null
                ? category.getBackItem()
                : module.getConfigManager().getBackPage();
        if (back != null && clicked.isSimilar(back.getItemStack())) {
            module.getConfigManager().openMainMenu(player);
            return;
        }
        ShopItem shopItem = category.getItemAt(clicked);
        if (shopItem != null) {
            module.getPurchaseManager().open(player, shopItem, category);
        }
    }

    private void handlePurchase(Player player, int slot, ItemStack clicked,
                                PurchaseHolder holder, Inventory inv) {
        var decline = module.getConfigManager().getDecline();
        var accept  = module.getConfigManager().getAccept();

        if (decline != null && clicked.isSimilar(decline.getItemStack())) {
            module.getCategoryManager().open(player, holder.getCategory());
            return;
        }
        if (accept != null && clicked.isSimilar(accept.getItemStack())) {
            executePurchase(player, holder);
            return;
        }

        int cur  = holder.getCurrentAmount();
        int max  = holder.getMaxAmount();
        int next = switch (slot) {
            case 9  -> Math.max(1, cur - 64);
            case 10 -> Math.max(1, cur - 16);
            case 11 -> Math.max(1, cur - 1);
            case 15 -> Math.min(max, cur + 1);
            case 16 -> Math.min(max, cur + 16);
            case 17 -> Math.min(max, cur + 64);
            default -> cur;
        };
        if (next != cur) {
            holder.setCurrentAmount(next);
            module.getPurchaseManager().refresh(inv);
        }
    }

    private void executePurchase(Player player, PurchaseHolder holder) {
        ShopItem shopItem = holder.getShopItem();
        int amount = holder.getCurrentAmount();
        double total = holder.getPricePerItem() * amount;

        RavenEconomy economy = module.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatFormatter.color("&cEconomy is not available."));
            return;
        }

        if (!economy.has(player, total)) {
            player.sendMessage(ChatFormatter.color(module.getConfigManager().getMsgNotEnoughBalance()));
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ClickAction action = shopItem.getIcon().getAction();
        boolean commandsOnly = action != null && !action.isEmpty();
        if (!commandsOnly && !canFit(player, shopItem.getIcon().getItemStack(), amount)) {
            player.sendMessage(ChatFormatter.color(module.getConfigManager().getMsgNotEnoughSpace()));
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        economy.withdrawPlayer(player, total);

        String itemName = "";
        var meta = shopItem.getIcon().getItemStack().getItemMeta();
        if (meta != null) itemName = meta.getDisplayName();

        player.sendMessage(ChatFormatter.color(module.getConfigManager().getMsgBuying()
                .replace("%amount%", String.valueOf(amount))
                .replace("%item-name%", itemName)));

        if (commandsOnly) {
            CommandHelper.executeCommand(player, action.commands());
        } else {
            ItemStack give = shopItem.getIcon().getItemStack().clone();
            give.setAmount(amount);
            player.getInventory().addItem(give);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private boolean canFit(Player player, ItemStack template, int amount) {
        if (template == null) return false;
        int remaining = amount;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (remaining <= 0) return true;
            if (slot == null) {
                remaining -= template.getMaxStackSize();
            } else if (template.isSimilar(slot) && slot.getAmount() < slot.getMaxStackSize()) {
                remaining -= slot.getMaxStackSize() - slot.getAmount();
            }
        }
        return remaining <= 0;
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }
}
