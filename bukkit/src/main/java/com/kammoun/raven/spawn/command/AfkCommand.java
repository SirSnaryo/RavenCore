package com.kammoun.raven.spawn.command;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.bungee.BungeeHelper;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.spawn.config.SpawnMessages;
import com.kammoun.raven.spawn.countdown.TeleportCountdown;

public class AfkCommand extends AbstractOpenMenuCommand {

    public AfkCommand(RavenCore api, BungeeHelper bungeeHelper, LocationCache cache,
                       NetworkPlayerManager networkPlayerManager, SpawnMessages messages, TeleportCountdown countdown) {
        super("afk", "raven.afk", LocationType.AFK, api, bungeeHelper, cache, networkPlayerManager, messages, countdown);
    }
}
