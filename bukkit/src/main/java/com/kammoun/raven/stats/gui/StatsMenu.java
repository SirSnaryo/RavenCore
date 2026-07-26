package com.kammoun.raven.stats.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.stats.model.StatsSnapshot;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class StatsMenu extends KMenu {
    private final RavenCore api;
    private final String shardsPlaceholder;
    private final List<MenuItem> templates = new ArrayList<>();
    private StatsSnapshot target;

    public StatsMenu(RavenCore api, String shardsPlaceholder) {
        super(api, "stats/gui/stats-menu.yml");
        this.api = api;
        this.shardsPlaceholder = shardsPlaceholder;
        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                MenuItem item = ItemReader.readMenuItem(items.getConfigurationSection(key), api);
                if (item != null) templates.add(item);
            }
        }
    }

    public void open(Player viewer, StatsSnapshot target) {
        this.target = target;
        super.open(viewer, placeholders(viewer, target));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        for (MenuItem template : templates) {
            ItemStack rendered = PlaceholderHelper.parsePlaceholders(template.getItemStack(), placeholders);
            setItem(MenuItem.of(new ItemBuilder(rendered)).slot(template.getSlot()));
        }
    }

    private String[] placeholders(Player viewer, StatsSnapshot stats) {
        double money = api.getEconomyModule() == null ? 0D
                : api.getEconomyModule().getEconomyService().getBalance(stats.uuid());
        String shards = "0";
        Player targetPlayer = Bukkit.getPlayer(stats.uuid());
        if (targetPlayer != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            shards = PlaceholderAPI.setPlaceholders(targetPlayer, shardsPlaceholder);
        }
        return new String[]{
                "%player%", stats.playerName(),
                "%kills%", NumberUtils.format(stats.kills()),
                "%deaths%", NumberUtils.format(stats.deaths()),
                "%kd%", String.format(Locale.US, "%.2f", stats.kd()),
                "%blocks_broken%", NumberUtils.format(stats.blocksBroken()),
                "%blocks_placed%", NumberUtils.format(stats.blocksPlaced()),
                "%mob_kills%", NumberUtils.format(stats.mobKills()),
                "%play_time%", formatDuration(stats.playTime()),
                "%items_crafted%", NumberUtils.format(stats.itemsCrafted()),
                "%jumps%", NumberUtils.format(stats.jumps()),
                "%chat_messages%", NumberUtils.format(stats.chatMessages()),
                "%money%", NumberUtils.format(money),
                "%shards%", shards,
                "%money_spent%", NumberUtils.format(stats.moneySpent()),
                "%money_made%", NumberUtils.format(stats.moneyMade())
        };
    }

    public static String formatDuration(long seconds) {
        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long remaining = seconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remaining + "s";
        return remaining + "s";
    }
}
