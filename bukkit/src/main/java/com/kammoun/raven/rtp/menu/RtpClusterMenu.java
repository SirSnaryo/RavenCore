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
import java.util.Map;

public final class RtpClusterMenu extends KMenu {
    private final RtpModule module;
    private final LocationDimension dimension;
    private final List<Integer> slots;
    private final MenuItem template;

    public RtpClusterMenu(RavenCore plugin, RtpModule module, LocationDimension dimension) {
        super(plugin, dimension == LocationDimension.NETHER
                ? "rtp/gui/nether-region-menu.yml"
                : "rtp/gui/region-menu.yml");
        this.module = module;
        this.dimension = dimension;
        this.slots = config.getIntegerList("Item-Slots");
        this.template = ItemReader.readMenuItem(config.getConfigurationSection("Items.Region"));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (getGoBack() != null) getGoBack().onClick(event -> module.openDimension((Player) event.getWhoClicked()));
        if (getClosePage() != null) getClosePage().onClick(event -> event.getWhoClicked().closeInventory());
        if (template == null) return;
        RtpModule.RtpOnlineSnapshot online = module.onlineSnapshot();
        int index = 0;
        for (Map.Entry<String, String> cluster : module.getRtpConfig().overworldClusters().entrySet()) {
            if (index >= slots.size()) break;
            List<String> servers = module.registeredServers(cluster.getKey(), dimension);
            ItemStack stack = RtpMenuPlaceholders.apply(template.getItemStack(), online, servers,
                    "%region%", cluster.getValue(), "%cluster%", cluster.getKey());
            MenuItem item = MenuItem.of(new ItemBuilder(stack)).slot(slots.get(index++));
            item.onClick(event -> {
                Player clicker = (Player) event.getWhoClicked();
                if (dimension == LocationDimension.NETHER) {
                    module.getTeleportService().nether(clicker, cluster.getKey());
                    return;
                }
                module.getTeleportService().overworld(clicker, cluster.getKey());
            });
            setItem(item);
        }
    }
}
