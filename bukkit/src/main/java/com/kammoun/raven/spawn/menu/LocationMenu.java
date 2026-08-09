package com.kammoun.raven.spawn.menu;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.location.NetworkLocation;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.spawn.LocationTransferHelper;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.spawn.config.SpawnMessages;
import com.kammoun.raven.spawn.countdown.TeleportCountdown;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One menu class for both /spawn and /afk, parameterized by LocationType. Entirely
 * config-driven: title/size/navigation come from KMenu/KConfigLoader, and the
 * available/full item templates + slot layout come from spawn-menu.yml / afk-menu.yml
 * (two separate files so each flavor's appearance can diverge without code changes).
 */
public class LocationMenu extends KMenu {

    private final RavenCore api;
    private final LocationType type;
    private final BungeeHelper bungeeHelper;
    private final LocationCache cache;
    private final NetworkPlayerManager networkPlayerManager;
    private final SpawnMessages messages;
    private final TeleportCountdown countdown;

    private final List<Integer> itemSlots;
    private final MenuItem availableTemplate;
    private final MenuItem fullTemplate;

    public LocationMenu(RavenCore api, LocationType type, BungeeHelper bungeeHelper, LocationCache cache,
                         NetworkPlayerManager networkPlayerManager, SpawnMessages messages, TeleportCountdown countdown) {
        super(api, "spawn/" + (type == LocationType.SPAWN ? "spawn-menu.yml" : "afk-menu.yml"));
        this.api = api;
        this.type = type;
        this.bungeeHelper = bungeeHelper;
        this.cache = cache;
        this.networkPlayerManager = networkPlayerManager;
        this.messages = messages;
        this.countdown = countdown;

        this.itemSlots = config.getIntegerList("Item-Slots");
        this.availableTemplate = ItemReader.readMenuItem(config.getConfigurationSection("Items.Available"));
        this.fullTemplate = ItemReader.readMenuItem(config.getConfigurationSection("Items.Full"));
    }

    @Override
    protected void setMenuItems(Player viewer, String... placeholders) {
        Map<String, Long> liveCounts = onlineByServer();

        List<NetworkLocation> servers = cache.getAll(type).stream()
                .filter(NetworkLocation::isEnabled)
                .sorted(Comparator.comparing(NetworkLocation::getServerName))
                .collect(Collectors.toList());

        int slotIndex = 0;
        for (NetworkLocation location : servers) {
            if (slotIndex >= itemSlots.size()) {
                api.getLogger().warning("[Spawn] More " + type + " servers than configured Item-Slots — some are not shown.");
                break;
            }
            int slot = itemSlots.get(slotIndex++);

            long count = liveCounts.getOrDefault(location.getServerName(), 0L);
            boolean full = location.getMaxPlayers() > 0 && count >= location.getMaxPlayers();
            MenuItem template = full ? fullTemplate : availableTemplate;
            if (template == null) continue;

            ItemStack stack = PlaceholderHelper.parsePlaceholders(template.getItemStack(),
                    "%server%", location.getServerName(),
                    "%number%", String.valueOf(slotIndex),
                    "%index%", String.valueOf(slotIndex),
                    "%count%", String.valueOf(count),
                    "%max%", String.valueOf(location.getMaxPlayers()));

            ItemBuilder builder = new ItemBuilder(stack);
            if (isCurrentLocation(viewer, location)) builder.glow();

            MenuItem item = MenuItem.of(builder).slot(slot);
            if (full) {
                item.onClick(event -> {
                    Player player = (Player) event.getWhoClicked();
                    countdown.playDenied(player);
                    player.sendMessage(messages.format("server-full", "server", location.getServerName()));
                });
            } else {
                item.onClick(event -> handleServerClick((Player) event.getWhoClicked(), location));
            }
            setItem(item);
        }
    }

    private Map<String, Long> onlineByServer() {
        if (networkPlayerManager == null) {
            return Map.of(api.getServerId(), (long) api.getServer().getOnlinePlayers().size());
        }
        Map<String, Long> counts = new HashMap<>();
        for (NetworkPlayerInfo info : networkPlayerManager.getOnlineNetworkPlayers()) {
            if (info.getServerId() == null || info.getServerId().isBlank()) continue;
            counts.merge(info.getServerId(), 1L, Long::sum);
        }
        return counts;
    }

    /** True if the viewer is currently standing on this server, in this location's world. */
    private boolean isCurrentLocation(Player viewer, NetworkLocation location) {
        return location.getServerName().equals(api.getServerId())
                && viewer.getWorld().getName().equals(location.getWorld());
    }

    private void handleServerClick(Player player, NetworkLocation destination) {
        player.closeInventory();
        if (api.getCombatModule() != null && !api.getCombatModule().canTransfer(player, true)) return;
        countdown.begin(player, destination.getServerName(), () -> teleport(player, destination));
    }

    private void teleport(Player player, NetworkLocation destination) {
        if (destination.getServerName().equals(api.getServerId())) {
            World world = Bukkit.getWorld(destination.getWorld());
            if (world == null) {
                countdown.playDenied(player);
                player.sendMessage(messages.get("world-missing"));
                return;
            }
            Location dest = new Location(world, destination.getX(), destination.getY(), destination.getZ(),
                    destination.getYaw(), destination.getPitch());
            player.getScheduler().run(api, t -> player.teleportAsync(dest), null);
            return;
        }

        LocationTransferHelper.transfer(api, api.getRedisMessenger().getConnection().getPool(),
                bungeeHelper, player, destination);
    }
}
