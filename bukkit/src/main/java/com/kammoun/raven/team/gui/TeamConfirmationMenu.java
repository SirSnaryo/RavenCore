package com.kammoun.raven.team.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import org.bukkit.entity.Player;

public final class TeamConfirmationMenu extends KMenu {
    private final Runnable confirm;
    private final Runnable cancel;
    private final MenuItem confirmItem;
    private final MenuItem cancelItem;

    public TeamConfirmationMenu(RavenCore plugin, String menuFile, Runnable confirm, Runnable cancel) {
        super(plugin, "team/gui/" + menuFile);
        this.confirm = confirm;
        this.cancel = cancel;
        this.confirmItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Confirm"));
        this.cancelItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Cancel"));
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
            cancelItem.onClick(event -> {
                player.closeInventory();
                cancel.run();
            });
            setItem(cancelItem);
        }
    }
}
