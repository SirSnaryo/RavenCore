package com.kammoun.raven.scoreboard.placeholder;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.scoreboard.stats.ScoreboardStatsService;
import com.kammoun.raven.stats.StatsModule;
import com.kammoun.raven.stats.model.StatsSnapshot;
import com.kammoun.raven.team.TeamModule;
import com.kammoun.raven.team.model.TeamSnapshot;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
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
        resolved = replace(resolved, "%raven_ravs%", () -> compactRavs(player));
        resolved = replace(resolved, "%raven_kills%", () -> NumberUtils.compact(kills(player)));
        resolved = replace(resolved, "%raven_deaths%", () -> NumberUtils.compact(deaths(player)));
        resolved = replace(resolved, "%raven_blocks_broken%", () -> NumberUtils.compact(snapshot(player).map(StatsSnapshot::blocksBroken).orElse(0L)));
        resolved = replace(resolved, "%raven_blocks_placed%", () -> NumberUtils.compact(snapshot(player).map(StatsSnapshot::blocksPlaced).orElse(0L)));
        resolved = replace(resolved, "%raven_mob_kills%", () -> NumberUtils.compact(snapshot(player).map(StatsSnapshot::mobKills).orElse(0L)));
        resolved = replace(resolved, "%raven_kd%", () -> snapshot(player).map(value -> String.format(java.util.Locale.US, "%.2f", value.kd())).orElse("0.00"));
        resolved = replace(resolved, "%raven_money_spent%", () -> NumberUtils.compactMoney(snapshot(player).map(StatsSnapshot::moneySpent).orElse(0D)));
        resolved = replace(resolved, "%raven_money_made%", () -> NumberUtils.compactMoney(snapshot(player).map(StatsSnapshot::moneyMade).orElse(0D)));
        resolved = replace(resolved, "%raven_playtime%", () -> formatPlaytimeSeconds(playtimeSeconds(player)));
        resolved = replace(resolved, "%raven_team%", () -> teamName(player));
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
        return NumberUtils.compactMoney(balance);
    }

    private String compactRavs(Player player) {
        StatsModule statsModule = plugin.getStatsModule();
        if (statsModule == null || statsModule.getRavs() == null) {
            return "0";
        }
        return NumberUtils.compact(statsModule.getRavs().current(player.getUniqueId()));
    }

    private long kills(Player player) {
        return snapshot(player).map(StatsSnapshot::kills).orElse((long) stats.kills(player));
    }

    private long deaths(Player player) {
        return snapshot(player).map(StatsSnapshot::deaths).orElse((long) stats.deaths(player));
    }

    private long playtimeSeconds(Player player) {
        return snapshot(player).map(StatsSnapshot::playTime).orElseGet(() -> stats.playtimeSeconds(player));
    }

    private Optional<StatsSnapshot> snapshot(Player player) {
        StatsModule statsModule = plugin.getStatsModule();
        if (statsModule == null || statsModule.getService() == null) {
            return Optional.empty();
        }
        return statsModule.getService().cached(player.getUniqueId());
    }

    private String teamName(Player player) {
        TeamModule teamModule = plugin.getTeamModule();
        if (teamModule == null || teamModule.getService() == null) {
            return "None";
        }
        return teamModule.getService().cached(player.getUniqueId())
                .map(TeamSnapshot::name)
                .orElse("None");
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
