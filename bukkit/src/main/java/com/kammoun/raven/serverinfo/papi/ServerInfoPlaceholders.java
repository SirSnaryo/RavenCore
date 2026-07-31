package com.kammoun.raven.serverinfo.papi;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.serverinfo.metrics.ServerMetrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * %ravenserver_server%           — proxy server id
 * %ravenserver_server_display%   — network.server-display-name, falls back to the id
 * %ravenserver_role%             — spawn / survival / ...
 * %ravenserver_online%           — players on this server
 * %ravenserver_network_online%   — players across the network (Redis)
 * %ravenserver_ping%             — viewer's ping, e.g. 42ms
 * %ravenserver_tps%              — global TPS, 1 minute average
 * %ravenserver_mspt%             — global average MSPT
 * %ravenserver_cpu%              — Java process CPU usage
 * %ravenserver_region_tps_low%   — region TPS, first average (region-threaded forks only)
 * %ravenserver_region_tps_mid%   — region TPS, second average
 */
public final class ServerInfoPlaceholders extends KPlaceholderExpansion {

    public ServerInfoPlaceholders(RavenCore plugin, ServerMetrics metrics) {
        super(plugin, "ravenserver");

        global("server", plugin::getServerId);
        global("server_display", plugin::getServerDisplayName);
        global("role", () -> role(plugin));
        global("online", () -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        global("network_online", () -> networkOnline(plugin));
        global("tps", metrics::tps);
        global("mspt", metrics::mspt);
        global("cpu", metrics::cpu);

        online("ping", player -> Math.max(0, player.getPing()) + "ms");
        online("region_tps_low", player -> metrics.regionTps(player, 0));
        online("region_tps_mid", player -> metrics.regionTps(player, 1));
    }

    private void global(String key, Supplier<String> supplier) {
        register(key, player -> orUnknown(supplier.get()));
    }

    /** Keys that need a live Player; resolves to N/A for offline lookups such as leaderboard rendering. */
    private void online(String key, Function<Player, String> handler) {
        register(key, offline -> {
            Player player = offline == null ? null : offline.getPlayer();
            return player == null ? ServerMetrics.UNKNOWN : orUnknown(handler.apply(player));
        });
    }

    private static String role(RavenCore plugin) {
        ServerRole role = plugin.getServerRole();
        return role == null ? ServerMetrics.UNKNOWN : role.name().toLowerCase(Locale.ROOT);
    }

    private static String networkOnline(RavenCore plugin) {
        if (plugin.getNetworkPlayerManager() == null) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }
        return String.valueOf(plugin.getNetworkPlayerManager().getNetworkPlayerNames().size());
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? ServerMetrics.UNKNOWN : value;
    }
}
