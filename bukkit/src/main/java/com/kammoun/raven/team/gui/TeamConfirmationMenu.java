package com.kammoun.raven.team.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.MenuItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class TeamConfirmationMenu extends KMenu {
    private final String confirmName;
    private final String[] confirmLore;
    private final Runnable confirm;
    private final Runnable cancel;

    public TeamConfirmationMenu(RavenCore plugin, String menuFile, String confirmName, String[] confirmLore,
                                Runnable confirm, Runnable cancel) {
        super(plugin, "team/gui/" + menuFile);
        this.confirmName = confirmName;
        this.confirmLore = confirmLore;
        this.confirm = confirm;
        this.cancel = cancel;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        setItem(MenuItem.of(ItemBuilder.of(Material.RED_CONCRETE).name("&c&lCancel")
                        .lore("&7Click to cancel"))
                .slot(11).onClick(event -> {
                    player.closeInventory();
                    cancel.run();
                }));
        setItem(MenuItem.of(ItemBuilder.of(Material.LIME_CONCRETE).name(confirmName).lore(confirmLore))
                .slot(15).onClick(event -> {
                    player.closeInventory();
                    confirm.run();
                }));
    }
}
