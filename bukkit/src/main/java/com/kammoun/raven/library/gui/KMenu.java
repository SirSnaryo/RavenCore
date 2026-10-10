package com.kammoun.raven.library.gui;

import com.kammoun.raven.library.config.KConfigLoader;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.kammoun.raven.library.util.PlaceholderHelper.parsePlaceholders;

@Getter
public abstract class KMenu extends KConfigLoader implements InventoryHolder {

    protected Inventory inventory;
    protected final Map<Integer, MenuItem> menuItems = new HashMap<>();

    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private MenuSession current;
    private final String title;
    private final int size;
    private MenuItem goBack;
    private MenuItem nextPage;
    private MenuItem prevPage;
    private MenuItem closePage;
    private MenuItem fillerItem;

    public KMenu(JavaPlugin plugin, String fileName) {
        super(plugin, fileName);
        this.title = config.getString("Title", "Default title");
        this.size = config.getInt("Size", 9);
        loadDefaultItems(config);
    }

    private void loadDefaultItems(@NotNull FileConfiguration config) {
        goBack     = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Go-Back"));
        nextPage   = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Next-Page"));
        prevPage   = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Prev-Page"));
        closePage  = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Close-Page"));
        fillerItem = ItemReader.readMenuItem(config.getConfigurationSection("Navigation.Filler-Item"));
    }

    protected abstract void setMenuItems(Player player, String... placeholders);

    protected void setItem(MenuItem item) {
        if (item == null || item.getSlot() < 0) return;
        menuItems.put(item.getSlot(), item);
        if (current != null) current.items().put(item.getSlot(), item);
        inventory.setItem(item.getSlot(), item.getItemStack());
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        MenuSession session = sessions.get(clicker.getUniqueId());
        Map<Integer, MenuItem> items = session == null ? menuItems : session.items();
        MenuItem item = items.get(event.getSlot());
        if (item != null) item.handleClick(event);
    }

    public void open(Player player, String... placeholders) {
        open(player, 1, 1, placeholders);
    }

    public void open(Player player, int currentPage, int maxPage, String... placeholders) {
        String formattedTitle = ChatFormatter.menuTitle(parsePlaceholders(title, placeholders));
        MenuSession session = new MenuSession(Bukkit.createInventory(this, size, formattedTitle),
                currentPage, maxPage);
        sessions.put(player.getUniqueId(), session);
        bind(session);

        setNavigationItems(currentPage, maxPage);
        setMenuItems(player, placeholders);
        addFillerItem();

        player.openInventory(inventory);
    }

    /**
     * Rebuilds the visible contents of an already open menu without closing and reopening it, so an
     * in-place toggle does not flicker the player's screen or desync the item they are holding.
     */
    public void refresh(Player player, String... placeholders) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            open(player, placeholders);
            return;
        }
        bind(session);
        inventory.setContents(new ItemStack[size]);
        menuItems.clear();
        session.items().clear();

        setNavigationItems(session.currentPage(), session.maxPage());
        setMenuItems(player, placeholders);
        addFillerItem();
    }

    private void bind(MenuSession session) {
        this.current = session;
        this.inventory = session.inventory();
        this.menuItems.clear();
        this.menuItems.putAll(session.items());
    }

    protected void setNavigationItems(int currentPage, int maxPage) {
        if (goBack != null)                             setItem(goBack);
        if (nextPage != null && currentPage < maxPage)  setItem(nextPage);
        if (prevPage != null && currentPage > 1)        setItem(prevPage);
        if (closePage != null) {
            closePage.onClick(event -> event.getWhoClicked().closeInventory());
            setItem(closePage);
        }
    }

    private void addFillerItem() {
        if (fillerItem == null) return;
        ItemStack filler = fillerItem.getItemStack();
        for (int slot = 0; slot < size; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private static final class MenuSession {
        private final Inventory inventory;
        private final Map<Integer, MenuItem> items = new HashMap<>();
        private final int currentPage;
        private final int maxPage;

        private MenuSession(Inventory inventory, int currentPage, int maxPage) {
            this.inventory = inventory;
            this.currentPage = currentPage;
            this.maxPage = maxPage;
        }

        private Inventory inventory() {
            return inventory;
        }

        private Map<Integer, MenuItem> items() {
            return items;
        }

        private int currentPage() {
            return currentPage;
        }

        private int maxPage() {
            return maxPage;
        }
    }
}
