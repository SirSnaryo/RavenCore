package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.rtp.RtpService;
import com.kammoun.raven.rtp.config.RtpDestinations;
import org.bukkit.entity.Player;

/**
 * Top-level /rtp menu: Overworld opens {@link RegionMenu}, Nether/End teleport
 * immediately since each is a single dedicated server with no sub-choice.
 */
public class DimensionMenu extends KMenu {

    private final MenuItem overworldItem;
    private final MenuItem netherItem;
    private final MenuItem endItem;

    private RegionMenu regionMenu;

    public DimensionMenu(RavenCore api, RtpService service, RtpDestinations destinations) {
        super(api, "rtp/dimension-menu.yml");

        this.overworldItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Overworld"));
        this.netherItem    = ItemReader.readMenuItem(config.getConfigurationSection("Items.Nether"));
        this.endItem       = ItemReader.readMenuItem(config.getConfigurationSection("Items.End"));

        if (overworldItem != null) {
            overworldItem.onClick(event -> regionMenu.open((Player) event.getWhoClicked()));
        }
        if (netherItem != null) {
            netherItem.onClick(event -> service.requestTeleport(
                    (Player) event.getWhoClicked(), destinations.netherServer(), "Nether"));
        }
        if (endItem != null) {
            endItem.onClick(event -> service.requestTeleport(
                    (Player) event.getWhoClicked(), destinations.endServer(), "End"));
        }
    }

    /** RegionMenu needs a DimensionMenu reference to go back to, so this is wired post-construction. */
    public void setRegionMenu(RegionMenu regionMenu) {
        this.regionMenu = regionMenu;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        setItem(overworldItem);
        setItem(netherItem);
        setItem(endItem);
    }
}
