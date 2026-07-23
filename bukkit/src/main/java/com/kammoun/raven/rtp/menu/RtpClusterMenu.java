package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.rtp.RtpModule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class RtpClusterMenu extends KMenu {
    private final RtpModule module;
    private final List<Integer> slots;
    private final MenuItem template;

    public RtpClusterMenu(RavenCore plugin, RtpModule module) {
        super(plugin, "rtp/gui/region-menu.yml");
        this.module = module;
        this.slots = config.getIntegerList("Item-Slots");
        this.template = ItemReader.readMenuItem(config.getConfigurationSection("Items.Region"));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        if (getGoBack() != null) getGoBack().onClick(event -> module.openDimension((Player) event.getWhoClicked()));
        if (getClosePage() != null) getClosePage().onClick(event -> event.getWhoClicked().closeInventory());
        if (template == null) return;
        int index = 0;
        for (Map.Entry<String, String> cluster : module.getRtpConfig().overworldClusters().entrySet()) {
            if (index >= slots.size()) break;
            ItemStack stack = PlaceholderHelper.parsePlaceholders(template.getItemStack(),
                    "%region%", cluster.getValue(), "%cluster%", cluster.getKey());
            MenuItem item = MenuItem.of(new ItemBuilder(stack)).slot(slots.get(index++));
            item.onClick(event -> module.getTeleportService().overworld(
                    (Player) event.getWhoClicked(), cluster.getKey()));
            setItem(item);
        }
    }
}
