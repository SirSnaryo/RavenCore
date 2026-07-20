package com.kammoun.raven.scoreboard.placeholder;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.scoreboard.stats.ScoreboardStatsService;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Supplier;

@Getter
public class ScoreboardPlaceholders {

    private final RavenCore plugin;
    private final ScoreboardServerMetrics metrics = new ScoreboardServerMetrics();
    private final ScoreboardStatsService stats;

    public ScoreboardPlaceholders(RavenCore plugin, ScoreboardStatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    public String apply(Player player, String input) {
        if (input == null || input.isEmpty()) return "";

        String resolved = input
                .replace("%player_name%", player.getName())
                .replace("%raven_player%", player.getName())
                .replace("%raven_world%", player.getWorld().getName())
                .replace("%raven_server%", plugin.getServerId())
                .replace("%raven_server_display%", plugin.getServerDisplayName())
                .replace("%raven_role%", plugin.getServerRole().name().toLowerCase())
                .replace("%raven_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        resolved = replace(resolved, "%raven_network_online%", () -> String.valueOf(plugin.getNetworkPlayerManager().getNetworkPlayerNames().size()));
        resolved = replace(resolved, "%raven_balance%", () -> balance(player));
        resolved = replace(resolved, "%raven_money%", () -> compactMoney(player));
        resolved = replace(resolved, "%raven_ravs%", () -> "0");
        resolved = replace(resolved, "%raven_kills%", () -> String.valueOf(stats.kills(player)));
        resolved = replace(resolved, "%raven_deaths%", () -> String.valueOf(stats.deaths(player)));
        resolved = replace(resolved, "%raven_playtime%", () -> formatPlaytimeSeconds(stats.playtimeSeconds(player)));
        resolved = replace(resolved, "%raven_ping%", () -> formatPing(player.getPing()));
        resolved = replace(resolved, "%raven_tps%", metrics::tps);
        resolved = replace(resolved, "%raven_mspt%", metrics::mspt);
        resolved = replace(resolved, "%raven_cpu%", metrics::cpu);
        resolved = replace(resolved, "%raven_low_tps_region%", () -> metrics.regionTps(player, 0));
        resolved = replace(resolved, "%raven_mid_tps_region%", () -> metrics.regionTps(player, 1));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }

        return resolved;
    }

    private static String replace(String input, String placeholder, Supplier<String> value) {
        if (!input.contains(placeholder)) {
            return input;
        }
        return input.replace(placeholder, value.get());
    }

    private String balance(Player player) {
        if (plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomy() == null) {
            return "0";
        }
        double balance = plugin.getEconomyModule().getEconomyService().getBalance(player.getUniqueId());
        return plugin.getEconomyModule().getEconomy().format(balance);
    }

    private String compactMoney(Player player) {
        if (plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomyService() == null) {
            return "0$";
        }
        double balance = plugin.getEconomyModule().getEconomyService().getBalance(player.getUniqueId());
        return formatCompactMoney(balance);
    }

    static String formatCompactMoney(double amount) {
        double absolute = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";

        if (absolute < 1_000D) {
            return sign + trimOneDecimal(absolute) + "$";
        }
        if (absolute < 1_000_000D) {
            return sign + trimOneDecimal(absolute / 1_000D) + "k";
        }
        if (absolute < 1_000_000_000D) {
            return sign + trimOneDecimal(absolute / 1_000_000D) + "m";
        }
        if (absolute < 1_000_000_000_000D) {
            return sign + trimOneDecimal(absolute / 1_000_000_000D) + "b";
        }
        return sign + trimOneDecimal(absolute / 1_000_000_000_000D) + "t";
    }

    private static String trimOneDecimal(double value) {
        double rounded = Math.round(value * 10D) / 10D;
        if (rounded == Math.rint(rounded)) {
            return String.format(Locale.US, "%.0f", rounded);
        }
        return String.format(Locale.US, "%.1f", rounded);
    }

    static String formatPlaytime(long ticks) {
        return formatPlaytimeSeconds(Math.max(0L, ticks) / 20L);
    }

    static String formatPlaytimeSeconds(long totalSeconds) {
        totalSeconds = Math.max(0L, totalSeconds);
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
