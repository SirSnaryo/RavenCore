package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.rtp.service.RtpService;
import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * One item per configured region, shown when picking a dimension from {@link DimensionMenu} —
 * used for both the Overworld's regions and, since each region also hosts its own local Nether,
 * the Nether's regions too.
 */
public class RegionMenu extends KMenu {

    private final RtpService service;
    private final Collection<RegionDestination> regions;

    private final List<Integer> itemSlots;
    private final MenuItem regionTemplate;

    private DimensionMenu backTarget;

    public RegionMenu(RavenCore api, RtpService service, Collection<RegionDestination> regions, String menuFile) {
        super(api, menuFile);
        this.service = service;
        this.regions = regions;

        this.itemSlots = config.getIntegerList("Item-Slots");
        this.regionTemplate = ItemReader.readMenuItem(config.getConfigurationSection("Items.Region"));

        if (getGoBack() != null) {
            getGoBack().onClick(event -> backTarget.open((Player) event.getWhoClicked()));
        }
    }

    /** DimensionMenu and its region menus reference each other, so this is wired post-construction. */
    public void setBackTarget(DimensionMenu backTarget) {
        this.backTarget = backTarget;
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (regionTemplate == null) return;

        int slotIndex = 0;
        for (RegionDestination region : regions) {
            if (slotIndex >= itemSlots.size()) break;
            int slot = itemSlots.get(slotIndex++);

            ItemStack stack = PlaceholderHelper.parsePlaceholders(regionTemplate.getItemStack(),
                    "{region}", region.displayName());

            MenuItem item = MenuItem.of(new ItemBuilder(stack)).slot(slot);
            item.onClick(event -> service.requestTeleport(
                    (Player) event.getWhoClicked(), region.id(), region.server(), region.displayName()));
            setItem(item);
        }
    }
}
