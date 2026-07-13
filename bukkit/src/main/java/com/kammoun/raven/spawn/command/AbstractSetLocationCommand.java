package com.kammoun.raven.spawn.command;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.location.NetworkLocation;
import com.kammoun.raven.library.redis.event.LocationsUpdatedEvent;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.spawn.config.SpawnMessages;
import com.kammoun.raven.spawn.db.LocationRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;

public abstract class AbstractSetLocationCommand extends KCommand {

    private final LocationType type;
    private final RavenCore api;
    private final LocationRepository repository;
    private final LocationCache cache;
    private final SpawnMessages messages;
    private final int defaultMaxPlayers;

    protected AbstractSetLocationCommand(String commandName, String permission, LocationType type,
                                          RavenCore api, LocationRepository repository, LocationCache cache,
                                          SpawnMessages messages, int defaultMaxPlayers) {
        super(commandName, permission, true);
        this.type = type;
        this.api = api;
        this.repository = repository;
        this.cache = cache;
        this.messages = messages;
        this.defaultMaxPlayers = defaultMaxPlayers;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender; // playerOnly=true guarantees this
        Location loc = player.getLocation();
        NetworkLocation location = NetworkLocation.builder()
                .serverName(api.getServerId())
                .type(type)
                .world(loc.getWorld().getName())
                .x(loc.getX())
                .y(loc.getY())
                .z(loc.getZ())
                .yaw(loc.getYaw())
                .pitch(loc.getPitch())
                .build();

        Bukkit.getAsyncScheduler().runNow(api, task -> {
            try {
                repository.upsert(location, defaultMaxPlayers);
                cache.reload();
                api.getNetworkEventBus().publish(new LocationsUpdatedEvent());
                player.getScheduler().run(api, t -> player.sendMessage(
                        messages.format("set.success", "type", type.name(), "server", api.getServerId())), null);
            } catch (SQLException e) {
                api.getLogger().warning("[Spawn] Failed to save " + type + " location: " + e.getMessage());
                player.getScheduler().run(api, t -> player.sendMessage(messages.get("set.failure")), null);
            }
        });
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
