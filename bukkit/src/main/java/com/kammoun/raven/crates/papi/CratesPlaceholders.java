package com.kammoun.raven.crates.papi;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.crates.config.CratesConfig;
import com.kammoun.raven.crates.player.CratePlayer;
import com.kammoun.raven.crates.player.CratePlayerManager;
import com.kammoun.raven.crates.service.CrateTimerService;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;

public final class CratesPlaceholders extends KPlaceholderExpansion {

    public CratesPlaceholders(RavenCore plugin, CratesConfig config, CrateTimerService timerService,
                              CratePlayerManager players) {
        super(plugin, "crates");
        register("time_left", player -> timerService.formattedTimeLeft());
        register("seconds_left", player -> String.valueOf(timerService.secondsLeft()));
        register("cycle", player -> String.valueOf(timerService.cycleId()));
        // kept for installs already using the doubled-prefix names
        register("crates_time_left", player -> timerService.formattedTimeLeft());
        register("crates_seconds_left", player -> String.valueOf(timerService.secondsLeft()));
        register("crates_cycle", player -> String.valueOf(timerService.cycleId()));
        register("keys", player -> {
            CratePlayer data = players.player(player.getUniqueId());
            return String.valueOf(data == null ? 0 : data.totalKeys());
        });
        registerPrefix("keys_", (player, crateId) -> {
            if (config.crate(crateId) == null) return null;
            return String.valueOf(players.keys(player.getUniqueId(), crateId));
        });
    }
}
