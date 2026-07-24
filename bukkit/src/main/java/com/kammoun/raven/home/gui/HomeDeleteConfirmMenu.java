package com.kammoun.raven.home.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import org.bukkit.entity.Player;

public final class HomeDeleteConfirmMenu extends KMenu {
    private final Runnable confirm;
    private final Runnable cancel;
    private final MenuItem confirmItem;
    private final MenuItem cancelItem;

    public HomeDeleteConfirmMenu(RavenCore plugin, Runnable confirm, Runnable cancel) {
        super(plugin, "home/gui/DeleteConfirm.yml");
        this.confirm = confirm;
        this.cancel = cancel;
        this.confirmItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Confirm-Delete"));
        this.cancelItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Cancel-Delete"));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (confirmItem != null) {
            confirmItem.onClick(event -> {
                player.closeInventory();
                confirm.run();
            });
            setItem(confirmItem);
        }
        if (cancelItem != null) {
            cancelItem.onClick(event -> cancel.run());
            setItem(cancelItem);
        }
    }
}
