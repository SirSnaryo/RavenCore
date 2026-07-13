package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.rtp.RtpService;
import com.kammoun.raven.rtp.config.RtpDestinations;
import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** One item per configured overworld region (e.g. NA/EU), shown when picking Overworld from {@link DimensionMenu}. */
public class RegionMenu extends KMenu {

    private final RtpService service;
    private final RtpDestinations destinations;

    private final List<Integer> itemSlots;
    private final MenuItem regionTemplate;

    public RegionMenu(RavenCore api, RtpService service, RtpDestinations destinations, DimensionMenu dimensionMenu) {
        super(api, "rtp/region-menu.yml");
        this.service = service;
        this.destinations = destinations;

        this.itemSlots = config.getIntegerList("Item-Slots");
        this.regionTemplate = ItemReader.readMenuItem(config.getConfigurationSection("Items.Region"));

        if (getGoBack() != null) {
            getGoBack().onClick(event -> dimensionMenu.open((Player) event.getWhoClicked()));
        }
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (regionTemplate == null) return;

        int slotIndex = 0;
        for (RegionDestination region : destinations.overworldWorlds()) {
            if (slotIndex >= itemSlots.size()) break;
            int slot = itemSlots.get(slotIndex++);

            ItemStack stack = PlaceholderHelper.parsePlaceholders(regionTemplate.getItemStack(),
                    "{region}", region.displayName());

            MenuItem item = MenuItem.of(new ItemBuilder(stack)).slot(slot);
            item.onClick(event -> service.requestTeleport(
                    (Player) event.getWhoClicked(), region.server(), region.displayName()));
            setItem(item);
        }
    }
}
