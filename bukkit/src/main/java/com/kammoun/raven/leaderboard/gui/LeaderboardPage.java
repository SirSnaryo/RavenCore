package com.kammoun.raven.leaderboard.gui;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.leaderboard.LeaderboardModule;
import com.kammoun.raven.leaderboard.model.LeaderboardEntry;
import com.kammoun.raven.leaderboard.model.LeaderboardType;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.skin.PlayerHeadUtil;
import com.kammoun.raven.library.skin.api.SkinCacheAPI;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.library.util.PlaceholderHelper;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class LeaderboardPage extends KMenu {
    private final LeaderboardModule module;
    private final LeaderboardType type;
    private final int page;
    private final int maxPage;
    private final MenuItem entryTemplate;
    private final MenuItem selfTemplate;
    private final MenuItem backTemplate;
    private final MenuItem refreshTemplate;
    private final MenuItem searchTemplate;
    private final List<Integer> entrySlots;

    public LeaderboardPage(RavenCore plugin, LeaderboardModule module, LeaderboardType type, int page) {
        super(plugin, "leaderboard/gui/page.yml");
        this.module = module;
        this.type = type;
        this.page = Math.max(1, page);
        this.maxPage = module.getService().maxPage(type);
        this.entryTemplate = loadEntryTemplate();
        this.selfTemplate = firstItem("Self-Item", "Items.Position");
        this.backTemplate = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Back"), plugin);
        this.refreshTemplate = firstItem("Navigation.Refresh", "Items.Refresh");
        this.searchTemplate = ItemReader.readMenuItem(config.getConfigurationSection("Items.Search"), plugin);
        List<Integer> slots = config.getIntegerList("Display-Slots");
        this.entrySlots = slots.isEmpty() ? config.getIntegerList("Entry-Slots") : slots;
    }

    public void open(Player player) {
        super.open(player,
                "%type%", typeTitle(),
                "%stats_type%", typeTitle(),
                "%page%", String.valueOf(page),
                "%max_page%", String.valueOf(maxPage));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        renderEntries(player);
        renderSelf(player);
        renderExtraNavigation(player);
    }

    private void renderEntries(Player player) {
        if (entryTemplate == null || entrySlots.isEmpty()) return;
        List<LeaderboardEntry> entries = module.getService().entries(type);
        int from = (page - 1) * module.getService().playersPerPage();
        for (int i = 0; i < entrySlots.size(); i++) {
            int index = from + i;
            if (index >= entries.size()) break;
            LeaderboardEntry entry = entries.get(index);
            int slot = entrySlots.get(i);
            MenuItem rendered = render(entryTemplate, slot, entry);
            setItem(rendered);
            renderCachedHead(player, slot, rendered.getItemStack(), entry.uuid());
        }
    }

    private void renderSelf(Player player) {
        if (selfTemplate == null) return;
        LeaderboardEntry entry = module.getService().findPlayer(type, player.getUniqueId());
        String rank = entry == null ? "N/A" : String.valueOf(entry.rank());
        String value = entry == null ? "N/A" : module.getService().format(type, entry.value());
        ItemStack rendered = PlaceholderHelper.parsePlaceholders(selfTemplate.getItemStack(),
                "%type%", typeTitle(),
                "%stats_type%", typeTitle(),
                "%rank%", rank,
                "%position%", rank,
                "%player%", player.getName(),
                "%name%", player.getName(),
                "%value%", value);
        setItem(MenuItem.of(new ItemBuilder(rendered)).slot(selfTemplate.getSlot()));
        renderCachedHead(player, selfTemplate.getSlot(), rendered, player.getUniqueId());
    }

    private void renderExtraNavigation(Player player) {
        if (backTemplate != null) {
            setItem(MenuItem.of(new ItemBuilder(backTemplate.getItemStack()))
                    .slot(backTemplate.getSlot())
                    .onClick(event -> module.openMenu(player)));
        }
        if (refreshTemplate != null) {
            setItem(MenuItem.of(new ItemBuilder(refreshTemplate.getItemStack()))
                    .slot(refreshTemplate.getSlot())
                    .onClick(event -> {
                        module.getService().refresh();
                        module.openPage(player, type, page);
                    }));
        }
        if (searchTemplate != null) {
            setItem(MenuItem.of(new ItemBuilder(searchTemplate.getItemStack())).slot(searchTemplate.getSlot()));
        }
    }

    @Override
    protected void setNavigationItems(int currentPage, int ignoredMaxPage) {
        MenuItem previous = nav("Prev", page > 1 ? page - 1 : -1);
        MenuItem next = nav("Next", page < maxPage ? page + 1 : -1);
        if (previous != null) setItem(previous);
        if (next != null) setItem(next);
    }

    private MenuItem nav(String key, int targetPage) {
        if (targetPage < 1) return null;
        ConfigurationSection section = config.getConfigurationSection("Navigation." + key);
        MenuItem item = ItemReader.readMenuItem(section, plugin);
        if (item == null) return null;
        return MenuItem.of(new ItemBuilder(item.getItemStack()))
                .slot(item.getSlot())
                .onClick(event -> module.openPage((Player) event.getWhoClicked(), type, targetPage));
    }

    private MenuItem render(MenuItem template, int slot, LeaderboardEntry entry) {
        ItemStack base = template.getItemStack();
        if (base.getType() == Material.PLAYER_HEAD) {
            base = base.clone();
        }
        ItemStack rendered = PlaceholderHelper.parsePlaceholders(base,
                "%type%", typeTitle(),
                "%stats_type%", typeTitle(),
                "%rank%", String.valueOf(entry.rank()),
                "%position%", String.valueOf(entry.rank()),
                "%player%", entry.playerName(),
                "%name%", entry.playerName(),
                "%uuid%", entry.uuid().toString(),
                "%value%", module.getService().format(type, entry.value()));
        return MenuItem.of(new ItemBuilder(rendered)).slot(slot);
    }

    private void renderCachedHead(Player viewer, int slot, ItemStack template, java.util.UUID uuid) {
        if (slot < 0 || template == null || template.getType() != Material.PLAYER_HEAD || !SkinCacheAPI.isInitialized()) {
            return;
        }
        PlayerHeadUtil.createHeadAsync(uuid).thenAccept(head ->
                SchedulerUtil.run(() -> {
                    if (!viewer.isOnline()
                            || viewer.getOpenInventory().getTopInventory().getHolder() != this) {
                        return;
                    }
                    inventory.setItem(slot, copyDisplay(template, head));
                }));
    }

    private ItemStack copyDisplay(ItemStack template, ItemStack head) {
        if (!template.hasItemMeta()) return head;
        ItemMeta source = template.getItemMeta();
        ItemMeta target = head.getItemMeta();
        if (source == null || target == null) return head;
        if (source.hasDisplayName()) target.setDisplayName(source.getDisplayName());
        if (source.hasLore()) target.setLore(source.getLore());
        target.addItemFlags(source.getItemFlags().toArray(new org.bukkit.inventory.ItemFlag[0]));
        if (source.hasCustomModelData()) target.setCustomModelData(source.getCustomModelData());
        head.setItemMeta(target);
        return head;
    }

    private String typeTitle() {
        String direct = config.getString("Titles." + type.key());
        if (direct != null) return direct;
        return switch (type) {
            case MOB_KILLS -> config.getString("Titles.mobs-killed", type.displayName());
            case BLOCKS_BROKEN -> config.getString("Titles.blocks-broken", type.displayName());
            case BLOCKS_PLACED -> config.getString("Titles.blocks-placed", type.displayName());
            case PLAY_TIME -> config.getString("Titles.play-time", type.displayName());
            case MONEY_SPENT -> config.getString("Titles.money-spent", type.displayName());
            case MONEY_MADE -> config.getString("Titles.sells-made", type.displayName());
            default -> type.displayName();
        };
    }

    private MenuItem firstItem(String firstPath, String secondPath) {
        MenuItem first = ItemReader.readMenuItem(config.getConfigurationSection(firstPath), plugin);
        return first != null ? first : ItemReader.readMenuItem(config.getConfigurationSection(secondPath), plugin);
    }

    private MenuItem loadEntryTemplate() {
        MenuItem item = ItemReader.readMenuItem(config.getConfigurationSection("Entry-Item"), plugin);
        if (item != null) return item;
        ConfigurationSection head = config.getConfigurationSection("Head");
        if (head == null) return null;
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD);
        if (head.contains("display-name")) builder.name(head.getString("display-name"));
        if (head.contains("name")) builder.name(head.getString("name"));
        if (head.contains("lore")) builder.lore(head.getStringList("lore"));
        return MenuItem.of(builder);
    }
}
