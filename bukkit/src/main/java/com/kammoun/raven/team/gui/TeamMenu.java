package com.kammoun.raven.team.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.team.TeamModule;
import com.kammoun.raven.team.model.TeamSnapshot;
import com.kammoun.raven.team.model.TeamSort;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import de.rapha149.signgui.SignGUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TeamMenu extends KMenu {
    private final TeamModule module;
    private final TeamSnapshot team;
    private final int page;
    private final TeamSort sort;
    private final String filter;
    private final List<Integer> teammateSlots;
    private final int maxPages;

    public TeamMenu(RavenCore plugin, TeamModule module, TeamSnapshot team,
                    int requestedPage, TeamSort sort, String filter) {
        super(plugin, "team/gui/team-menu.yml");
        this.module = module;
        this.team = team;
        this.sort = sort;
        this.filter = filter;
        this.teammateSlots = config.getIntegerList("teammate-slots");
        int count = members().size();
        this.maxPages = Math.max(1, (int) Math.ceil(count / (double) teammateSlots.size()));
        this.page = Math.max(1, Math.min(requestedPage, maxPages));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        List<TeamSnapshot.Member> members = members();
        MenuItem invite = item("Items.invite");
        for (int slot : teammateSlots) {
            if (invite != null) setItem(cloneAt(invite, slot));
        }
        int start = (page - 1) * teammateSlots.size();
        int end = Math.min(start + teammateSlots.size(), members.size());
        for (int index = start; index < end; index++) {
            TeamSnapshot.Member member = members.get(index);
            int slot = teammateSlots.get(index - start);
            setItem(MenuItem.of(new ItemBuilder(memberHead(member))).slot(slot).onClick(event -> {
                if (member.uuid().equals(player.getUniqueId())) return;
                if (team.isLeader(member.uuid())) {
                    module.getMessages().send(player, "cannot-modify-leader");
                    return;
                }
                new TeamPermissionMenu(module.getApi(), module, team, member).open(player);
            }));
        }

        MenuItem search = item("Items.search");
        if (search != null) {
            search.onClick(event -> {
                player.closeInventory();
                try {
                    SignGUI.builder()
                            .setType(Material.OAK_SIGN)
                            .setLine(1, "↑↑↑↑↑↑↑↑↑↑↑↑")
                            .setLine(2, "SEARCH")
                            .setHandler((searchPlayer, result) -> {
                                String input = result.getLineWithoutColor(0).trim();
                                SchedulerUtil.execute(searchPlayer, () ->
                                        module.openMenu(searchPlayer, 1, sort, input.isEmpty() ? null : input));
                                return List.of();
                            })
                            .build()
                            .open(player);
                } catch (Throwable exception) {
                    module.getMessages().send(player, "search-unavailable");
                }
            });
            setItem(search);
        }
        MenuItem sortItem = item("Items.sort");
        if (sortItem != null) {
            ItemStack stack = sortItem.getItemStack();
            ItemMeta meta = stack.getItemMeta();
            List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
            sort.lore().forEach(line -> lore.add(ChatFormatter.color(line)));
            meta.setLore(lore);
            stack.setItemMeta(meta);
            MenuItem finalSort = MenuItem.of(new ItemBuilder(stack)).slot(sortItem.getSlot())
                    .onClick(event -> module.openMenu(player, page, sort.next(), filter));
            setItem(finalSort);
        }
        MenuItem teamItem = replaced(item("Items.team"), "%team_name%", team.name(),
                "%max_players%", String.valueOf(module.getMaxMembers()));
        if (teamItem != null) {
            teamItem.onClick(event -> module.openMenu(player, page, sort, filter));
            setItem(teamItem);
        }
        MenuItem home = item("Items.team-home");
        if (home != null) {
            home.onClick(event -> module.getService().home(player));
            setItem(home);
        }
        MenuItem pvp = item(team.pvpEnabled() ? "Items.pvp-on" : "Items.pvp-off");
        if (pvp != null) {
            pvp.onClick(event ->{
                        module.getService().togglePvp(player);
                        module.openMenu(player, page, sort, filter);
            } );
            setItem(pvp);
        }
        if (getPrevPage() != null && page > 1) {
            getPrevPage().onClick(event -> module.openMenu(player, page - 1, sort, filter));
            setItem(getPrevPage());
        }
        if (getNextPage() != null && page < maxPages) {
            getNextPage().onClick(event -> module.openMenu(player, page + 1, sort, filter));
            setItem(getNextPage());
        }
    }

    @Override
    public void open(Player player, String... ignored) {
        super.open(player, "%page%", String.valueOf(page), "%max_page%", String.valueOf(maxPages));
    }

    private List<TeamSnapshot.Member> members() {
        List<TeamSnapshot.Member> values = new ArrayList<>(team.members());
        if (filter != null && !filter.isBlank()) {
            String lower = filter.toLowerCase(Locale.ROOT);
            values.removeIf(member -> !member.lastName().toLowerCase(Locale.ROOT).contains(lower));
        }
        switch (sort) {
            case JOIN_DATE -> values.sort(Comparator.comparingLong(TeamSnapshot.Member::joinedAt));
            case PERMISSIONS -> values.sort(Comparator.comparingInt(
                    (TeamSnapshot.Member member) -> Integer.bitCount(member.permissions())).reversed());
            case MONEY -> { }
            case ALPHABETICALLY -> values.sort(Comparator.comparing(TeamSnapshot.Member::lastName,
                    String.CASE_INSENSITIVE_ORDER));
            case ONLINE_MEMBERS -> values.sort(Comparator.comparing(member ->
                    !module.getApi().getNetworkPlayerManager().getNetworkPlayerNames().contains(member.lastName())));
        }
        return values;
    }

    private ItemStack memberHead(TeamSnapshot.Member member) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(member.uuid());
        meta.setOwningPlayer(offline);
        boolean online = module.getApi().getNetworkPlayerManager().getNetworkPlayerNames().stream()
                .anyMatch(name -> name.equalsIgnoreCase(member.lastName()));
        meta.setDisplayName(ChatFormatter.color((online ? "&a● " : "&c● ") + "&#00f986" + member.lastName()));
        meta.setLore(List.of(ChatFormatter.color("&fClick to manage")));
        stack.setItemMeta(meta);
        return stack;
    }

    private MenuItem item(String path) {
        return ItemReader.readMenuItem(config.getConfigurationSection(path));
    }

    private MenuItem cloneAt(MenuItem source, int slot) {
        return MenuItem.of(new ItemBuilder(source.getItemStack())).slot(slot)
                .onClick(event -> {
                    event.getWhoClicked().closeInventory();
                    module.getMessages().send(player(event), "team-invite-usage");
                });
    }

    private Player player(org.bukkit.event.inventory.InventoryClickEvent event) {
        return (Player) event.getWhoClicked();
    }

    private MenuItem replaced(MenuItem source, String... replacements) {
        if (source == null) return null;
        ItemStack stack = source.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        String name = meta.getDisplayName();
        List<String> lore = meta.getLore();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            name = name.replace(replacements[i], replacements[i + 1]);
            if (lore != null) {
                int index = i;
                lore = lore.stream().map(line -> line.replace(replacements[index], replacements[index + 1])).toList();
            }
        }
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return MenuItem.of(new ItemBuilder(stack)).slot(source.getSlot());
    }
}
