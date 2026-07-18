package com.kammoun.raven.scoreboard.placeholder;

import com.kammoun.raven.RavenCore;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Getter
public class ScoreboardPlaceholders {

    private final RavenCore plugin;

    public ScoreboardPlaceholders(RavenCore plugin) {
        this.plugin = plugin;
    }

    public String apply(Player player, String input) {
        if (input == null || input.isEmpty()) return "";

        String resolved = input
                .replace("%player_name%", player.getName())
                .replace("%raven_player%", player.getName())
                .replace("%raven_world%", player.getWorld().getName())
                .replace("%raven_server%", plugin.getServerId())
                .replace("%raven_role%", plugin.getServerRole().name().toLowerCase())
                .replace("%raven_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%raven_network_online%", String.valueOf(plugin.getNetworkPlayerManager().getNetworkPlayerNames().size()))
                .replace("%raven_balance%", balance(player));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }

        return resolved;
    }

    private String balance(Player player) {
        if (plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomy() == null) {
            return "0";
        }
        double balance = plugin.getEconomyModule().getEconomyService().getBalance(player.getUniqueId());
        return plugin.getEconomyModule().getEconomy().format(balance);
    }
}
