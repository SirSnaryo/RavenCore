package com.kammoun.raven.spawn.command;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.spawn.config.SpawnMessages;
import com.kammoun.raven.spawn.countdown.TeleportCountdown;
import com.kammoun.raven.spawn.menu.LocationMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public abstract class AbstractOpenMenuCommand extends KCommand {

    private final LocationType type;
    private final LocationCache cache;
    private final SpawnMessages messages;
    private final LocationMenu menu;

    protected AbstractOpenMenuCommand(String commandName, String permission, LocationType type,
                                       RavenCore api, BungeeHelper bungeeHelper, LocationCache cache,
                                       NetworkPlayerManager networkPlayerManager, SpawnMessages messages,
                                       TeleportCountdown countdown) {
        super(commandName, permission, true);
        this.type = type;
        this.cache = cache;
        this.messages = messages;
        this.menu = new LocationMenu(api, type, bungeeHelper, cache, networkPlayerManager, messages, countdown);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender; // playerOnly=true guarantees this
        if (!cache.isLoaded()) {
            player.sendMessage(messages.get("not-ready"));
            return;
        }
        if (cache.getAll(type).isEmpty()) {
            player.sendMessage(messages.format("none-available", "type", type.name()));
            return;
        }
        menu.open(player);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
