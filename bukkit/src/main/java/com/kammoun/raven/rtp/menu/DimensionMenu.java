package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.rtp.RtpService;
import com.kammoun.raven.rtp.config.RtpDestinations;
import org.bukkit.entity.Player;

/**
 * Top-level /rtp menu: Overworld and Nether each open their own {@link RegionMenu} — every
 * survival region hosts its own local Nether, so the Nether is split per-region just like the
 * Overworld. End teleports immediately since it's still a single dedicated server.
 */
public class DimensionMenu extends KMenu {

    private final MenuItem overworldItem;
    private final MenuItem netherItem;
    private final MenuItem endItem;

    public DimensionMenu(RavenCore api, RtpService service, RtpDestinations destinations,
                          RegionMenu overworldRegionMenu, RegionMenu netherRegionMenu) {
        super(api, "rtp/dimension-menu.yml");

        this.overworldItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Overworld"));
        this.netherItem    = ItemReader.readMenuItem(config.getConfigurationSection("Items.Nether"));
        this.endItem       = ItemReader.readMenuItem(config.getConfigurationSection("Items.End"));

        if (overworldItem != null) {
            overworldItem.onClick(event -> overworldRegionMenu.open((Player) event.getWhoClicked()));
        }
        if (netherItem != null) {
            netherItem.onClick(event -> netherRegionMenu.open((Player) event.getWhoClicked()));
        }
        if (endItem != null) {
            destinations.end().ifPresent(end -> endItem.onClick(event -> service.requestTeleport(
                    (Player) event.getWhoClicked(), end.id(), end.server(), end.displayName())));
        }
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        setItem(overworldItem);
        setItem(netherItem);
        setItem(endItem);
    }
}
