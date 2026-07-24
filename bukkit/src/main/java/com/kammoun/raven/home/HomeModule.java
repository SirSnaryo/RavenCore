package com.kammoun.raven.home;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.home.command.HomeCommand;
import com.kammoun.raven.home.config.HomeConfig;
import com.kammoun.raven.home.config.HomeMessages;
import com.kammoun.raven.home.db.HomeRepository;
import com.kammoun.raven.home.db.HomeTable;
import com.kammoun.raven.home.api.HomeDestination;
import com.kammoun.raven.home.api.HomeTeleportGateway;
import com.kammoun.raven.home.gui.HomeMenu;
import com.kammoun.raven.home.model.HomeRecord;
import com.kammoun.raven.home.service.HomeService;
import com.kammoun.raven.home.service.HomeWarmup;
import com.kammoun.raven.library.module.KModule;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class HomeModule extends KModule<RavenCore> implements Listener {
    private final Map<UUID, List<String>> suggestions = new ConcurrentHashMap<>();
    private HomeConfig homeConfig;
    private HomeMessages messages;
    private HomeWarmup warmup;
    private HomeService service;

    public HomeModule() {
        super("home");
    }

    @Override
    public void onModuleEnable() {
        if (!api.getMySqlManager().isConnected()) {
            getLogger().severe("[Home] Disabled because MySQL is unavailable.");
            return;
        }
        new HomeTable(api.getMySqlManager()).createTable();
        loadState();
        HomeCommand command = new HomeCommand(this);
        registerCommand(command, "home");
        registerCommand(command, "sethome");
        registerCommand(command, "delhome");
        registerCommand(command, "homes");
        registerEvent(this);
        getLogger().info("[Home] Network-wide homes enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (warmup != null) warmup.stop();
        suggestions.clear();
    }

    @Override
    public void reload() {
        if (warmup != null) warmup.stop();
        reloadConfig();
        loadState();
    }

    public void openMenu(Player player) {
        service.list(player, homes -> {
            cacheSuggestions(player.getUniqueId(), homes.stream().map(HomeRecord::name).toList());
            if (api.getTeamModule() == null || api.getTeamModule().getService() == null) {
                new HomeMenu(api, this, homes, java.util.Optional.empty()).open(player);
                return;
            }
            api.getTeamModule().getService().query(player,
                    team -> new HomeMenu(api, this, homes, team).open(player));
        });
    }

    public void cacheSuggestions(UUID uuid, List<String> names) {
        suggestions.put(uuid, List.copyOf(names));
    }

    public List<String> getSuggestions(UUID uuid) {
        return suggestions.getOrDefault(uuid, List.of());
    }

    /**
     * Stable integration API for modules that own a shared home, such as teams.
     */
    public HomeTeleportGateway getTeleportGateway() {
        return service;
    }

    public void teleport(Player player, HomeDestination destination) {
        service.teleport(player, destination);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        warmup.onMove(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        warmup.onDamage(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        suggestions.remove(event.getPlayer().getUniqueId());
        warmup.onQuit(event);
    }

    private void loadState() {
        homeConfig = HomeConfig.load(getConfig());
        messages = new HomeMessages(getConfig());
        warmup = new HomeWarmup(api, homeConfig, messages);
        service = new HomeService(api, new HomeRepository(api.getMySqlManager()),
                homeConfig, messages, warmup);
    }
}
