package com.kammoun.raven.spawn.command;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.location.LocationType;
import com.kammoun.raven.spawn.cache.LocationCache;
import com.kammoun.raven.spawn.config.SpawnMessages;
import com.kammoun.raven.spawn.db.LocationRepository;

public class SetAfkCommand extends AbstractSetLocationCommand {

    public SetAfkCommand(RavenCore api, LocationRepository repository, LocationCache cache,
                          SpawnMessages messages, int defaultMaxPlayers) {
        super("setafk", "raven.setafk", LocationType.AFK, api, repository, cache, messages, defaultMaxPlayers);
    }
}
