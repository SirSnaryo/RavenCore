package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.rtp.service.RtpService;
import com.kammoun.raven.rtp.config.RtpDestinations;
import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Top-level /rtp menu. The Overworld opens its own {@link RegionMenu} so players can pick a
 * region. The Nether and End both teleport immediately: the End is a single dedicated server,
 * and the Nether is deliberately server-blind — players don't choose which Nether they land in,
 * one of the network's Nether servers is picked at random.
 */
public class DimensionMenu extends KMenu {

    private final MenuItem overworldItem;
    private final MenuItem netherItem;
    private final MenuItem endItem;

    public DimensionMenu(RavenCore api, RtpService service, RtpDestinations destinations,
                          RegionMenu overworldRegionMenu, String netherLabel) {
        super(api, "rtp/dimension-menu.yml");

        this.overworldItem = ItemReader.readMenuItem(config.getConfigurationSection("Items.Overworld"));
        this.netherItem    = ItemReader.readMenuItem(config.getConfigurationSection("Items.Nether"));
        this.endItem       = ItemReader.readMenuItem(config.getConfigurationSection("Items.End"));

        if (overworldItem != null) {
            overworldItem.onClick(event -> overworldRegionMenu.open((Player) event.getWhoClicked()));
        }

        List<RegionDestination> netherDestinations = new ArrayList<>(destinations.netherWorlds());
        if (netherItem != null && !netherDestinations.isEmpty()) {
            netherItem.onClick(event -> {
                RegionDestination dest = netherDestinations.get(
                        ThreadLocalRandom.current().nextInt(netherDestinations.size()));
                service.requestTeleport((Player) event.getWhoClicked(), dest.id(), dest.server(), netherLabel);
            });
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
