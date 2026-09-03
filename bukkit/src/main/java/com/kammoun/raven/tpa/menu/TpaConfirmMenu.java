package com.kammoun.raven.tpa.menu;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.tpa.TeleportModule;
import com.kammoun.raven.tpa.model.TpaRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
                displayServer(request.requesterServer(), request.requesterServerDisplayName()),
                localWorld(request.requesterId()), request.requesterId()));
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        ConfigurationSection section = config.getConfigurationSection("Items");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            MenuItem item = ItemReader.readMenuItem(section.getConfigurationSection(key), plugin);
            if (item == null) continue;
            ItemStack stack = PlaceholderHelper.parsePlaceholders(item.getItemStack(), placeholders);
            if (key.equalsIgnoreCase("Location")) {
                Material material = Material.matchMaterial(placeholder(placeholders, "%location_material%"));
                if (material != null) {
                    stack.setType(material);
                }
            }
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
                "%server%", "",
                "%world%", world == null ? "Unknown" : module.getTeleportConfig().worldNickname(world),
                "%ping%", pingText(playerId),
                "%is_flying%", pingText(playerId),
                "%location_material%", locationMaterial(world)
        };
    }

    private String displayServer(String id, String displayName) {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    private String localWorld(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? null : player.getWorld().getName();
    }

    private String pingText(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return "Unknown";
        }
        return player.getPing() + "ms";
    }

    private String locationMaterial(String world) {
        if (world == null) {
            return "GRASS_BLOCK";
        }
        String normalized = world.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("nether")) {
            return "NETHERRACK";
        }
        if (normalized.contains("end")) {
            return "END_STONE";
        }
        return "GRASS_BLOCK";
    }

    private String placeholder(String[] placeholders, String key) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            if (placeholders[i].equalsIgnoreCase(key)) {
                return placeholders[i + 1];
            }
        }
        return "";
    }

    public enum Mode {
        REQUEST,
        ACCEPT
    }

    private record Context(NetworkPlayerInfo target, TpaRequest request) {
    }
}
