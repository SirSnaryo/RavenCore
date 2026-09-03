package com.kammoun.raven.scoreboard.placeholder;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.scoreboard.stats.ScoreboardStatsService;
import com.kammoun.raven.stats.StatsModule;
import com.kammoun.raven.stats.model.StatsSnapshot;
import com.kammoun.raven.team.TeamModule;
import com.kammoun.raven.team.model.TeamSnapshot;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Getter
public class ScoreboardPlaceholders {

    private static final String UNKNOWN = "N/A";
    private static final Set<String> KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            "player",
            "player_name",
            "world",
            "server",
            "server_display",
            "role",
            "server_role",
            "online",
            "network_online",
            "balance",
            "balance_formatted",
            "balance_compact",
            "balance_full",
            "balance_raw",
            "money",
            "money_formatted",
            "ravs",
            "ravs_balance",
            "ravs_balance_formatted",
            "kills",
            "deaths",
            "blocks_broken",
            "blocks_placed",
            "mob_kills",
            "kd",
            "money_spent",
            "money_made",
            "playtime",
            "play_time",
            "team",
            "ping",
            "tps",
            "mspt",
            "cpu",
            "low_tps_region",
            "mid_tps_region",
            "region_tps_low",
            "region_tps_mid"
    )));

    private final RavenCore plugin;
    private final ScoreboardServerMetrics metrics = new ScoreboardServerMetrics();
    private final ScoreboardStatsService stats;

    public ScoreboardPlaceholders(RavenCore plugin, ScoreboardStatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    public String apply(Player player, String input) {
        if (input == null || input.isEmpty()) return "";

        String resolved = input.replace("%player_name%", player.getName());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }

        resolved = applyInternal(player, resolved);
        return resolved;
    }

    public Set<String> keys() {
        return KEYS;
    }

    public String value(OfflinePlayer player, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }

        return switch (key.toLowerCase(Locale.ROOT)) {
            case "player", "player_name" -> playerName(player);
            case "world" -> online(player, online -> online.getWorld().getName());
            case "server" -> safe(plugin.getServerId());
            case "server_display" -> serverDisplay();
            case "role", "server_role" -> role();
            case "online" -> NumberUtils.compact(Bukkit.getOnlinePlayers().size());
            case "network_online" -> networkOnline();
            case "balance", "balance_formatted", "balance_compact", "money", "money_formatted" -> compactMoney(player);
            case "balance_full" -> fullMoney(player);
            case "balance_raw" -> rawMoney(player);
            case "ravs", "ravs_balance", "ravs_balance_formatted" -> compactRavs(player);
            case "kills" -> NumberUtils.compact(kills(player));
            case "deaths" -> NumberUtils.compact(deaths(player));
            case "blocks_broken" -> NumberUtils.compact(snapshot(player).map(StatsSnapshot::blocksBroken).orElse(0L));
            case "blocks_placed" -> NumberUtils.compact(snapshot(player).map(StatsSnapshot::blocksPlaced).orElse(0L));
            case "mob_kills" -> NumberUtils.compact(snapshot(player).map(StatsSnapshot::mobKills).orElse(0L));
            case "kd" -> snapshot(player).map(value -> String.format(Locale.US, "%.2f", value.kd())).orElse("0.00");
            case "money_spent" -> NumberUtils.compactMoney(snapshot(player).map(StatsSnapshot::moneySpent).orElse(0D));
            case "money_made" -> NumberUtils.compactMoney(snapshot(player).map(StatsSnapshot::moneyMade).orElse(0D));
            case "playtime", "play_time" -> formatPlaytimeSeconds(playtimeSeconds(player));
            case "team" -> teamName(player);
            case "ping" -> online(player, online -> formatPing(online.getPing()));
            case "tps" -> metrics.tps();
            case "mspt" -> metrics.mspt();
            case "cpu" -> metrics.cpu();
            case "low_tps_region", "region_tps_low" -> online(player, online -> metrics.regionTps(online, 0));
            case "mid_tps_region", "region_tps_mid" -> online(player, online -> metrics.regionTps(online, 1));
            default -> null;
        };
    }

    private String applyInternal(Player player, String input) {
        String resolved = input;
        for (String key : KEYS) {
            resolved = replace(resolved, "%raven_" + key + "%", () -> value(player, key));
        }
        return resolved;
    }

    private static String replace(String input, String placeholder, Supplier<String> value) {
        if (!input.contains(placeholder)) {
            return input;
        }
        String replacement = value.get();
        return input.replace(placeholder, replacement == null ? "" : replacement);
    }

    private String fullMoney(OfflinePlayer player) {
        double balance = balanceAmount(player);
        RavenEconomy economy = economy();
        return economy == null ? NumberUtils.format(balance) : economy.formatFull(balance);
    }

    private String compactMoney(OfflinePlayer player) {
        double balance = balanceAmount(player);
        RavenEconomy economy = economy();
        return economy == null ? NumberUtils.compactMoney(balance) : economy.formatCompact(balance);
    }

    private String rawMoney(OfflinePlayer player) {
        return Double.toString(balanceAmount(player));
    }

    private double balanceAmount(OfflinePlayer player) {
        UUID uuid = uuid(player);
        if (uuid == null || plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomyService() == null) {
            return 0D;
        }
        return plugin.getEconomyModule().getEconomyService().getBalance(uuid);
    }

    private RavenEconomy economy() {
        if (plugin.getEconomyModule() == null || plugin.getEconomyModule().getEconomy() == null) {
            return null;
        }
        return plugin.getEconomyModule().getEconomy();
    }

    private String compactRavs(OfflinePlayer player) {
        UUID uuid = uuid(player);
        StatsModule statsModule = plugin.getStatsModule();
        if (uuid == null || statsModule == null || statsModule.getRavs() == null) {
            return "0";
        }
        return NumberUtils.compact(statsModule.getRavs().current(uuid));
    }

    private long kills(OfflinePlayer player) {
        Optional<StatsSnapshot> snapshot = snapshot(player);
        if (snapshot.isPresent()) {
            return snapshot.get().kills();
        }
        Player online = onlinePlayer(player);
        return online == null ? 0L : stats.kills(online);
    }

    private long deaths(OfflinePlayer player) {
        Optional<StatsSnapshot> snapshot = snapshot(player);
        if (snapshot.isPresent()) {
            return snapshot.get().deaths();
        }
        Player online = onlinePlayer(player);
        return online == null ? 0L : stats.deaths(online);
    }

    private long playtimeSeconds(OfflinePlayer player) {
        Optional<StatsSnapshot> snapshot = snapshot(player);
        if (snapshot.isPresent()) {
            return snapshot.get().playTime();
        }
        Player online = onlinePlayer(player);
        return online == null ? 0L : stats.playtimeSeconds(online);
    }

    private Optional<StatsSnapshot> snapshot(OfflinePlayer player) {
        UUID uuid = uuid(player);
        StatsModule statsModule = plugin.getStatsModule();
        if (uuid == null || statsModule == null || statsModule.getService() == null) {
            return Optional.empty();
        }
        return statsModule.getService().cached(uuid);
    }

    private String teamName(OfflinePlayer player) {
        UUID uuid = uuid(player);
        TeamModule teamModule = plugin.getTeamModule();
        if (uuid == null || teamModule == null || teamModule.getService() == null) {
            return "None";
        }
        return teamModule.getService().cached(uuid)
                .map(TeamSnapshot::name)
                .orElse("None");
    }

    private String networkOnline() {
        if (plugin.getNetworkPlayerManager() == null) {
            return NumberUtils.compact(Bukkit.getOnlinePlayers().size());
        }
        return NumberUtils.compact(plugin.getNetworkPlayerManager().getNetworkPlayerNames().size());
    }

    private String serverDisplay() {
        String display = plugin.getServerDisplayName();
        if (display == null || display.isBlank()) {
            return safe(plugin.getServerId());
        }
        return display;
    }

    private String role() {
        return plugin.getServerRole() == null ? UNKNOWN : plugin.getServerRole().name().toLowerCase(Locale.ROOT);
    }

    private String playerName(OfflinePlayer player) {
        if (player == null) {
            return UNKNOWN;
        }
        String name = player.getName();
        return name == null || name.isBlank() ? UNKNOWN : name;
    }

    private String online(OfflinePlayer player, java.util.function.Function<Player, String> handler) {
        Player online = onlinePlayer(player);
        if (online == null) {
            return UNKNOWN;
        }
        return safe(handler.apply(online));
    }

    private Player onlinePlayer(OfflinePlayer player) {
        return player == null ? null : player.getPlayer();
    }

    private UUID uuid(OfflinePlayer player) {
        return player == null ? null : player.getUniqueId();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
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
