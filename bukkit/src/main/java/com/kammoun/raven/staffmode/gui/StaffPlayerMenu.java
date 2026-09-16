package com.kammoun.raven.staffmode.gui;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StaffPlayerMenu extends KMenu {

    private static final int PLAYER_SLOT_LIMIT = 45;

    private final InvSeeService invSeeService;
    private final View view;
    private final String filter;
    private final int page;

    public StaffPlayerMenu(JavaPlugin plugin, InvSeeService invSeeService) {
        this(plugin, invSeeService, View.OVERVIEW, "");
    }

    public StaffPlayerMenu(JavaPlugin plugin, InvSeeService invSeeService, View view) {
        this(plugin, invSeeService, view, "");
    }

    public StaffPlayerMenu(JavaPlugin plugin, InvSeeService invSeeService, View view, String filter) {
        this(plugin, invSeeService, view, filter, 1);
    }

    private StaffPlayerMenu(JavaPlugin plugin, InvSeeService invSeeService, View view, String filter, int page) {
        super(plugin, fileName(view));
        this.invSeeService = invSeeService;
        this.view = view == null ? View.OVERVIEW : view;
        this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        this.page = Math.max(1, page);
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        List<TargetEntry> targets = targets();
        int maxPage = Math.max(1, (int) Math.ceil(targets.size() / (double) PLAYER_SLOT_LIMIT));
        int currentPage = Math.min(page, maxPage);
        int from = (currentPage - 1) * PLAYER_SLOT_LIMIT;
        int to = Math.min(targets.size(), from + PLAYER_SLOT_LIMIT);
        int slot = 0;
        for (int index = from; index < to; index++) {
            setItem(playerItem(player, targets.get(index), slot++));
        }
        setControls(currentPage, maxPage);
    }

    private List<TargetEntry> targets() {
        Map<UUID, TargetEntry> entries = new LinkedHashMap<>();
        Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(player -> entries.put(player.getUniqueId(), TargetEntry.online(player)));

        int offlineLimit = Math.max(0, config.getInt("Offline-Limit", 180));
        if (offlineLimit > 0) {
            List<OfflinePlayer> offlinePlayers = new ArrayList<>(List.of(Bukkit.getOfflinePlayers()));
            offlinePlayers.sort(Comparator
                    .comparingLong(OfflinePlayer::getLastSeen).reversed()
                    .thenComparing(player -> player.getName() == null ? "" : player.getName(), String.CASE_INSENSITIVE_ORDER));
            int added = 0;
            for (OfflinePlayer offline : offlinePlayers) {
                if (added >= offlineLimit) break;
                String name = offline.getName();
                if (name == null || name.isBlank()) continue;
                entries.putIfAbsent(offline.getUniqueId(), TargetEntry.offline(offline));
                added++;
            }
        }

        return entries.values().stream()
                .filter(entry -> filter.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(filter))
                .toList();
    }

    private void setControls(int currentPage, int maxPage) {
        setItem(MenuItem.of(new ItemBuilder(Material.ARROW)
                        .name("&#4CC9F0Back")
                        .lore("&#7A7A7ASTAFF", "", "&#FFFFFFReturn to staff menu."))
                .slot(45)
                .onClick(event -> new StaffMenu(plugin, invSeeService).open((Player) event.getWhoClicked())));
        if (currentPage > 1) {
            setItem(MenuItem.of(new ItemBuilder(Material.SPECTRAL_ARROW)
                            .name("&#FFD447Previous")
                            .lore("&#7A7A7APAGE", "", "&#FFFFFFPage " + (currentPage - 1) + " of " + maxPage + "."))
                    .slot(48)
                    .onClick(event -> new StaffPlayerMenu(plugin, invSeeService, view, filter, currentPage - 1)
                            .open((Player) event.getWhoClicked())));
        }
        setItem(MenuItem.of(new ItemBuilder(Material.BARRIER)
                        .name("&#D90429Close")
                        .lore("&#7A7A7ASTAFF", "", "&#FFFFFFClose this menu."))
                .slot(49)
                .onClick(event -> event.getWhoClicked().closeInventory()));
        if (currentPage < maxPage) {
            setItem(MenuItem.of(new ItemBuilder(Material.SPECTRAL_ARROW)
                            .name("&#33FF66Next")
                            .lore("&#7A7A7APAGE", "", "&#FFFFFFPage " + (currentPage + 1) + " of " + maxPage + "."))
                    .slot(50)
                    .onClick(event -> new StaffPlayerMenu(plugin, invSeeService, view, filter, currentPage + 1)
                            .open((Player) event.getWhoClicked())));
        }
    }

    private MenuItem playerItem(Player viewer, TargetEntry target, int slot) {
        return MenuItem.of(new ItemBuilder(target.online() ? head(target.player()) : new ItemStack(Material.PLAYER_HEAD))
                        .name((target.online() ? "&#FFD447" : "&#7A7A7A") + target.name())
                        .lore(
                                target.online() ? "&#7A7A7AONLINE PLAYER" : "&#7A7A7AOFFLINE PLAYER",
                                "",
                                "&#FFFFFFStatus: " + (target.online() ? "&#33FF66Online" : "&#D90429Offline"),
                                "&#FFFFFFWorld: &#4CC9F0" + (target.online() ? target.player().getWorld().getName() : "Saved data"),
                                "&#FFFFFFPing: &#4CC9F0" + (target.online() ? target.player().getPing() + "ms" : "n/a"),
                                "&#FFFFFFMode: &#A855F7" + (target.online() ? target.player().getGameMode().name() : "n/a"),
                                "",
                                "&#33FF66Click &#7A7A7Aopen " + view.label() + " tools",
                                "&#4CC9F0/staff " + view.command() + " <name> &#7A7A7Ato search"
                        ))
                .slot(slot)
                .onClick(event -> {
                    if (target.online()) {
                        new StaffPlayerDetailMenu(plugin, invSeeService, target.player(), view).open(viewer,
                                "%player%", target.name());
                    } else {
                        new StaffPlayerDetailMenu(plugin, invSeeService, Bukkit.getOfflinePlayer(target.id()), target.name(), view)
                                .open(viewer, "%player%", target.name());
                    }
                });
    }

    private MenuItem offlineItem(Player viewer, OfflinePlayer target, int slot) {
        String name = target.getName() == null ? filter : target.getName();
        return MenuItem.of(new ItemBuilder(Material.PLAYER_HEAD)
                        .name("&#7A7A7A" + name)
                        .lore(
                                "&#7A7A7AOFFLINE PLAYER",
                                "",
                                "&#FFFFFFStatus: &#D90429Offline",
                                "",
                                "&#33FF66Click &#7A7A7Aopen saved data tools"
                        ))
                .slot(slot)
                .onClick(event -> new StaffPlayerDetailMenu(plugin, invSeeService, target, name, view).open(viewer,
                        "%player%", name));
    }

    private ItemStack head(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    public View view() {
        return view;
    }

    private static String fileName(View view) {
        View selected = view == null ? View.OVERVIEW : view;
        return switch (selected) {
            case OVERVIEW -> "staffmode/players-menu.yml";
            case INVENTORY -> "staffmode/inventory-menu.yml";
            case PUNISHMENTS -> "staffmode/punishments-menu.yml";
            case HISTORY -> "staffmode/history-menu.yml";
        };
    }

    public enum View {
        OVERVIEW("player", "players"),
        INVENTORY("inventory", "inventory"),
        PUNISHMENTS("punishment", "punishments"),
        HISTORY("history", "history");

        private final String label;
        private final String command;

        View(String label, String command) {
            this.label = label;
            this.command = command;
        }

        public String label() {
            return label;
        }

        public String command() {
            return command;
        }
    }

    private record TargetEntry(UUID id, String name, Player player, boolean online) {
        private static TargetEntry online(Player player) {
            return new TargetEntry(player.getUniqueId(), player.getName(), player, true);
        }

        private static TargetEntry offline(OfflinePlayer player) {
            String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
            return new TargetEntry(player.getUniqueId(), name, null, false);
        }
    }
}
