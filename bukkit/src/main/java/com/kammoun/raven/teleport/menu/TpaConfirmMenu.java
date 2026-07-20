package com.kammoun.raven.teleport.menu;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.teleport.TeleportModule;
import com.kammoun.raven.teleport.model.TpaRequest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaConfirmMenu extends KMenu {

    private final TeleportModule module;
    private final Mode mode;
    private final TpaRequest.Type type;
    private final Map<UUID, Context> contexts = new ConcurrentHashMap<>();

    public TpaConfirmMenu(JavaPlugin plugin, String fileName, TeleportModule module, Mode mode, TpaRequest.Type type) {
        super(plugin, fileName);
        this.module = module;
        this.mode = mode;
        this.type = type;
    }

    public void open(Player player, NetworkPlayerInfo target) {
        contexts.put(player.getUniqueId(), new Context(target, null));
        super.open(player, placeholders(target.getPlayerName(), displayServer(target.getServerId(), target.getServerDisplayName()),
                target.getWorld(), target.getUuid()));
    }

    public void open(Player player, TpaRequest request) {
        contexts.put(player.getUniqueId(), new Context(null, request));
        super.open(player, placeholders(request.requesterName(),
                displayServer(request.requesterServer(), request.requesterServerDisplayName()), null, request.requesterId()));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        ConfigurationSection section = config.getConfigurationSection("Items");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            MenuItem item = ItemReader.readMenuItem(section.getConfigurationSection(key), plugin);
            if (item == null) continue;
            ItemStack stack = PlaceholderHelper.parsePlaceholders(item.getItemStack(), placeholders);
            MenuItem display = MenuItem.of(new com.kammoun.raven.library.item.ItemBuilder(stack))
                    .slot(item.getSlot())
                    .permission(item.getPermission())
                    .onClick(event -> click(player, key, event));
            setItem(display);
        }
    }

    private void click(Player player, String key, InventoryClickEvent event) {
        Context context = contexts.remove(player.getUniqueId());
        if (context == null) {
            player.closeInventory();
            return;
        }

        if (key.equalsIgnoreCase("Cancel")) {
            player.closeInventory();
            module.getTeleportConfig().sound(player, "request-cancelled");
            return;
        }

        if (!key.equalsIgnoreCase("Confirm")) {
            contexts.put(player.getUniqueId(), context);
            module.getTeleportConfig().sound(player, "information-click");
            return;
        }

        player.closeInventory();
        if (mode == Mode.REQUEST && context.target != null) {
            module.getTpaManager().request(player, context.target, type);
            return;
        }

        if (mode == Mode.ACCEPT && context.request != null) {
            module.getTpaManager().acceptRequest(player, context.request);
        }
    }

    private String[] placeholders(String playerName, String server, String world, UUID playerId) {
        return new String[]{
                "%player%", playerName == null ? "Unknown" : playerName,
                "%server%", server == null ? "Unknown" : server,
                "%world%", world == null ? "Unknown" : module.getTeleportConfig().worldNickname(world),
                "%is_flying%", flyingText(playerId)
        };
    }

    private String displayServer(String id, String displayName) {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    private String flyingText(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null) {
            return "Unknown";
        }
        return player.isFlying() ? "Yes" : "No";
    }

    public enum Mode {
        REQUEST,
        ACCEPT
    }

    private record Context(NetworkPlayerInfo target, TpaRequest request) {
    }
}
