package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.rtp.RtpModule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class RtpDimensionMenu extends KMenu {
    private final RtpModule module;
    private final MenuItem overworld;
    private final MenuItem nether;
    private final MenuItem end;

    public RtpDimensionMenu(RavenCore plugin, RtpModule module) {
        super(plugin, "rtp/gui/rtp.yml");
        this.module = module;
        this.overworld = ItemReader.readMenuItem(config.getConfigurationSection("Items.Overworld"));
        this.nether = ItemReader.readMenuItem(config.getConfigurationSection("Items.Nether"));
        this.end = ItemReader.readMenuItem(config.getConfigurationSection("Items.End"));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (getClosePage() != null) getClosePage().onClick(event -> event.getWhoClicked().closeInventory());
        RtpModule.RtpOnlineSnapshot online = module.onlineSnapshot();
        if (overworld != null) {
            List<String> servers = module.registeredServersAcrossClusters(LocationDimension.OVERWORLD);
            MenuItem item = item(overworld, RtpMenuPlaceholders.apply(
                    overworld.getItemStack(), online, servers));
            item.onClick(event -> module.openClusters((Player) event.getWhoClicked(), LocationDimension.OVERWORLD));
            setItem(item);
        }
        if (nether != null) {
            List<String> servers = module.registeredServersAcrossClusters(LocationDimension.NETHER);
            MenuItem item = item(nether, RtpMenuPlaceholders.apply(
                    nether.getItemStack(), online, servers));
            item.onClick(event -> module.openClusters((Player) event.getWhoClicked(), LocationDimension.NETHER));
            setItem(item);
        }
        if (end != null) {
            List<String> servers = List.of(module.getRtpConfig().endServer());
            MenuItem item = item(end, RtpMenuPlaceholders.apply(
                    end.getItemStack(), online, servers));
            item.onClick(event -> module.getTeleportService().end((Player) event.getWhoClicked()));
            setItem(item);
        }
    }

    private MenuItem item(MenuItem template, ItemStack stack) {
        return MenuItem.of(new ItemBuilder(stack)).slot(template.getSlot());
    }
}
