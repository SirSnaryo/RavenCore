package com.kammoun.raven.scoreboard.placeholder;

import com.kammoun.raven.RavenCore;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
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
                .replace("%raven_balance%", balance(player))
                .replace("%raven_money%", balance(player))
                .replace("%raven_ravs%", balance(player))
                .replace("%raven_kills%", String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS)))
                .replace("%raven_deaths%", String.valueOf(player.getStatistic(Statistic.DEATHS)))
                .replace("%raven_playtime%", formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)))
                .replace("%raven_ping%", formatPing(player.getPing()));

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

    static String formatPlaytime(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    static String formatPing(int ping) {
        return Math.max(0, ping) + "ms";
    }
}
