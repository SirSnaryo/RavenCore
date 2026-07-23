package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.rtp.RtpModule;
import org.bukkit.entity.Player;

public final class RtpDimensionMenu extends KMenu {
    private final RtpModule module;
    private final MenuItem overworld;
    private final MenuItem nether;
    private final MenuItem end;

    public RtpDimensionMenu(RavenCore plugin, RtpModule module) {
        super(plugin, "rtp/gui/dimension-menu.yml");
        this.module = module;
        this.overworld = ItemReader.readMenuItem(config.getConfigurationSection("Items.Overworld"));
        this.nether = ItemReader.readMenuItem(config.getConfigurationSection("Items.Nether"));
        this.end = ItemReader.readMenuItem(config.getConfigurationSection("Items.End"));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (getClosePage() != null) getClosePage().onClick(event -> event.getWhoClicked().closeInventory());
        if (overworld != null) {
            overworld.onClick(event -> module.openClusters((Player) event.getWhoClicked()));
            setItem(overworld);
        }
        if (nether != null) {
            nether.onClick(event -> module.getTeleportService().nether((Player) event.getWhoClicked()));
            setItem(nether);
        }
        if (end != null) {
            end.onClick(event -> module.getTeleportService().end((Player) event.getWhoClicked()));
            setItem(end);
        }
    }
}
