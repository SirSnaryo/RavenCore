package com.kammoun.raven.stats.papi;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.stats.gui.StatsMenu;
import com.kammoun.raven.stats.model.StatsSnapshot;
import com.kammoun.raven.stats.ravs.RavsBalanceResolver;
import com.kammoun.raven.stats.service.StatsService;

import java.util.Locale;
import java.util.function.Function;

public final class StatsPlaceholders extends KPlaceholderExpansion {
    public StatsPlaceholders(RavenCore plugin, StatsService service, RavsBalanceResolver ravs) {
        super(plugin, "stats");
        stat("kills", service, s -> NumberUtils.format(s.kills()));
        stat("deaths", service, s -> NumberUtils.format(s.deaths()));
        stat("kd", service, s -> String.format(Locale.US, "%.2f", s.kd()));
        stat("blocks_broken", service, s -> NumberUtils.format(s.blocksBroken()));
        stat("blocks_placed", service, s -> NumberUtils.format(s.blocksPlaced()));
        stat("mob_kills", service, s -> NumberUtils.format(s.mobKills()));
        stat("play_time", service, s -> StatsMenu.formatDuration(s.playTime()));
        stat("money_spent", service, s -> NumberUtils.format(s.moneySpent()));
        stat("money_made", service, s -> NumberUtils.format(s.moneyMade()));
        register("money", player -> NumberUtils.compactMoney(
                plugin.getEconomyModule().getEconomyService().getBalance(player.getUniqueId())));
        register("ravs", player -> NumberUtils.compact(ravs.current(player.getUniqueId())));
    }

    private void stat(String key, StatsService service, Function<StatsSnapshot, String> formatter) {
        register(key, player -> service.cached(player.getUniqueId()).map(formatter).orElse("0"));
    }
}
