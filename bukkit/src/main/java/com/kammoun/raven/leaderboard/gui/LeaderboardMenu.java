package com.kammoun.raven.leaderboard.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.leaderboard.LeaderboardModule;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.PlaceholderHelper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class LeaderboardMenu extends KMenu {
    private final LeaderboardModule module;
    private final Map<LeaderboardType, MenuItem> templates = new HashMap<>();

    public LeaderboardMenu(RavenCore plugin, LeaderboardModule module) {
        super(plugin, "leaderboard/gui/menu.yml");
        this.module = module;
        loadItems();
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        for (Map.Entry<LeaderboardType, MenuItem> entry : templates.entrySet()) {
            LeaderboardType type = entry.getKey();
            if (!module.getService().enabled(type)) continue;
            MenuItem template = entry.getValue();
            ItemStack rendered = PlaceholderHelper.parsePlaceholders(template.getItemStack(),
                    "%type%", type.displayName(),
                    "%key%", type.key());
            setItem(MenuItem.of(new ItemBuilder(rendered))
                    .slot(template.getSlot())
                    .permission(template.getPermission())
                    .onClick(event -> module.openPage(player, type, 1)));
        }
    }

    private void loadItems() {
        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) continue;
            LeaderboardType type = LeaderboardType.parse(section.getString("type", key));
            if (type == null) continue;
            MenuItem item = ItemReader.readMenuItem(section, plugin);
            if (item != null) templates.put(type, item);
        }
    }
}
