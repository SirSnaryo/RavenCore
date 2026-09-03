package com.kammoun.raven.stats.papi;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.hook.RavenEconomy;
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
        stat("kills", service, s -> NumberUtils.compact(s.kills()));
        stat("deaths", service, s -> NumberUtils.compact(s.deaths()));
        stat("kd", service, s -> String.format(Locale.US, "%.2f", s.kd()));
        stat("blocks_broken", service, s -> NumberUtils.compact(s.blocksBroken()));
        stat("blocks_placed", service, s -> NumberUtils.compact(s.blocksPlaced()));
        stat("mob_kills", service, s -> NumberUtils.compact(s.mobKills()));
        stat("play_time", service, s -> StatsMenu.formatDuration(s.playTime()));
        stat("money_spent", service, s -> NumberUtils.compactMoney(s.moneySpent()));
        stat("money_made", service, s -> NumberUtils.compactMoney(s.moneyMade()));
        register("money", player -> formatMoney(plugin, balance(plugin, player.getUniqueId())));
        register("ravs", player -> NumberUtils.compact(ravs.current(player.getUniqueId())));
    }

    private void stat(String key, StatsService service, Function<StatsSnapshot, String> formatter) {
        register(key, player -> service.cached(player.getUniqueId()).map(formatter).orElse("0"));
    }

    private double balance(RavenCore plugin, java.util.UUID uuid) {
        if (plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomyService() == null) {
            return 0D;
        }
        return plugin.getEconomyModule().getEconomyService().getBalance(uuid);
    }

    private String formatMoney(RavenCore plugin, double amount) {
        if (plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomy() == null) {
            return NumberUtils.compactMoney(amount);
        }
        RavenEconomy economy = plugin.getEconomyModule().getEconomy();
        return economy.formatCompact(amount);
    }
}
