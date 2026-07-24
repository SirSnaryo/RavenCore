package com.kammoun.raven.economy.papi;

import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.economy.service.EconomyService;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyPlaceholders extends KPlaceholderExpansion {

    public EconomyPlaceholders(JavaPlugin plugin, String identifier, EconomyService service, RavenEconomy economy) {
        super(plugin, identifier);

        register("money", player -> compact(player, service, economy));
        register("money_formatted", player -> compact(player, service, economy));
        register("balance_compact", player -> compact(player, service, economy));
        register("balance", player -> balance(player, service, economy));
        register("balance_formatted", player -> balance(player, service, economy));
        register("balance_raw", player -> raw(player, service));
    }

    private String compact(OfflinePlayer player, EconomyService service, RavenEconomy economy) {
        return economy.formatCompact(amount(player, service));
    }

    private String balance(OfflinePlayer player, EconomyService service, RavenEconomy economy) {
        return economy.format(amount(player, service));
    }

    private String raw(OfflinePlayer player, EconomyService service) {
        return String.valueOf(amount(player, service));
    }

    private double amount(OfflinePlayer player, EconomyService service) {
        return player == null ? 0D : service.getBalance(player.getUniqueId());
    }
}
