package com.kammoun.raven.economy.confirm;

import com.kammoun.raven.economy.model.PendingEconomyAction;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.PlaceholderHelper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyConfirmationMenu extends KMenu {

    private final EconomyConfirmationService service;
    private final Map<UUID, PendingEconomyAction> contexts = new ConcurrentHashMap<>();

    public EconomyConfirmationMenu(JavaPlugin plugin, String fileName, EconomyConfirmationService service) {
        super(plugin, fileName);
        this.service = service;
    }

    public void open(Player player, PendingEconomyAction action, String formattedAmount, String currentBalance, String afterBalance) {
        contexts.put(player.getUniqueId(), action);
        super.open(player,
                "%player%", action.targetName(),
                "%amount%", formattedAmount,
                "%amount_formatted%", formattedAmount,
                "%balance%", currentBalance,
                "%balance_formatted%", currentBalance,
                "%after%", afterBalance,
                "%after_formatted%", afterBalance,
                "%reason%", action.reason(),
                "%expires%", secondsLeft(action) + "s");
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        ConfigurationSection section = config.getConfigurationSection("Items");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            MenuItem item = ItemReader.readMenuItem(section.getConfigurationSection(key), plugin);
            if (item == null) continue;
            ItemStack stack = PlaceholderHelper.parsePlaceholders(item.getItemStack(), placeholders);
            MenuItem display = MenuItem.of(new com.kammoun.raven.library.item.ItemBuilder(stack))
                    .slot(item.getSlot())
                    .permission(item.getPermission())
                    .onClick(event -> click(player, key, event));
            setItem(display);
        }
    }

    private void click(Player player, String key, InventoryClickEvent event) {
        PendingEconomyAction action = contexts.remove(player.getUniqueId());
        if (action == null) {
            player.closeInventory();
            return;
        }

        if (key.equalsIgnoreCase("Cancel")) {
            player.closeInventory();
            service.cancel(player, action);
            return;
        }

        if (!key.equalsIgnoreCase("Confirm")) {
            contexts.put(player.getUniqueId(), action);
            return;
        }

        player.closeInventory();
        service.confirm(player, action);
    }

    private long secondsLeft(PendingEconomyAction action) {
        return Math.max(0L, (action.expiresAt() - System.currentTimeMillis()) / 1_000L);
    }
}
