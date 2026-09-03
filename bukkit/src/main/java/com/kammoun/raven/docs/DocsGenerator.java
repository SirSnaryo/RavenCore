package com.kammoun.raven.docs;

import com.kammoun.raven.RavenCore;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DocsGenerator {
    private DocsGenerator() {}

    public static void write(RavenCore plugin) {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.writeString(plugin.getDataFolder().toPath().resolve("docs.txt"), render(plugin),
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().warning("[Docs] Could not write docs.txt: " + error.getMessage());
        }
    }

    private static String render(RavenCore plugin) {
        StringBuilder out = new StringBuilder(16_384);
        PluginDescriptionFile description = plugin.getDescription();
        out.append("RavenCore Server Docs\n");
        out.append("=====================\n\n");
        out.append("Generated: ").append(Instant.now()).append('\n');
        out.append("Version: ").append(description.getVersion()).append('\n');
        out.append("Server ID: ").append(plugin.getServerId()).append('\n');
        out.append("Server Display: ").append(plugin.getServerDisplayName()).append('\n');
        out.append("Server Role: ").append(plugin.getServerRole()).append("\n\n");

        out.append("Enabled Modules\n");
        out.append("---------------\n");
        for (String module : plugin.getEnabledModules()) {
            out.append("- ").append(module).append('\n');
        }
        out.append('\n');

        out.append("Commands\n");
        out.append("--------\n");
        Map<String, Map<String, Object>> commands = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        commands.putAll(description.getCommands());
        commands.forEach((name, data) -> {
            out.append('/').append(name).append('\n');
            appendValue(out, "  usage", data.get("usage"));
            appendValue(out, "  aliases", data.get("aliases"));
            appendValue(out, "  permission", data.get("permission"));
            appendValue(out, "  description", data.get("description"));
        });
        out.append('\n');

        out.append("Permissions\n");
        out.append("-----------\n");
        List<Permission> permissions = description.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (Permission permission : permissions) {
            out.append(permission.getName())
                    .append(" | default=")
                    .append(permission.getDefault())
                    .append(" | ")
                    .append(permission.getDescription() == null ? "" : permission.getDescription())
                    .append('\n');
        }
        out.append('\n');

        out.append("PlaceholderAPI\n");
        out.append("--------------\n");
        appendPlaceholders(out, "Raven scoreboard/network", List.of(
                "%raven_server%", "%raven_server_display%", "%raven_role%", "%raven_world%",
                "%raven_online%", "%raven_network_online%", "%raven_balance%", "%raven_money%",
                "%raven_ravs%", "%raven_kills%", "%raven_deaths%", "%raven_blocks_broken%",
                "%raven_blocks_placed%", "%raven_mob_kills%", "%raven_kd%", "%raven_money_spent%",
                "%raven_money_made%", "%raven_playtime%", "%raven_team%", "%raven_ping%",
                "%raven_tps%", "%raven_mspt%", "%raven_cpu%", "%raven_low_tps_region%",
                "%raven_mid_tps_region%"));
        appendPlaceholders(out, "Stats", List.of(
                "%stats_money%", "%stats_ravs%", "%stats_kills%", "%stats_deaths%", "%stats_kd%",
                "%stats_blocks_broken%", "%stats_blocks_placed%", "%stats_mob_kills%",
                "%stats_play_time%", "%stats_money_spent%", "%stats_money_made%"));
        appendPlaceholders(out, "Economy", List.of(
                "%ravencore_money%", "%ravencore_money_formatted%", "%ravencore_balance_compact%",
                "%ravencore_balance%", "%ravencore_balance_formatted%", "%ravencore_balance_full%",
                "%ravencore_balance_raw%"));
        appendPlaceholders(out, "Leaderboard", List.of(
                "%leaderboard_<type>_name_<position>%",
                "%leaderboard_<type>_value_<position>%",
                "%leaderboard_<type>_raw_<position>%",
                "types: money, kills, deaths, mob_kills, blocks_broken, blocks_placed, play_time, money_spent, money_made"));
        appendPlaceholders(out, "Crates/Billford/RTP Zone", List.of(
                "%crates_time_left%", "%crates_seconds_left%", "%crates_cycle%",
                "%crates_keys%", "%crates_keys_<crate>%",
                "%billford_current%", "%billford_timeleft%", "%billford_next_change_date%",
                "%billford_count%", "%rtpzone_time%"));

        out.append('\n');
        out.append("Config Files\n");
        out.append("------------\n");
        for (String path : List.of(
                "config.yml", "chat/config.yml", "scoreboard/config.yml", "leaderboard/config.yml",
                "leaderboard/gui/menu.yml", "leaderboard/gui/page.yml", "rtp/config.yml",
                "locationpool/config.yml", "respawn/config.yml", "borders/config.yml",
                "spawn/config.yml", "teleport/config.yml", "economy/config.yml", "global/config.yml")) {
            out.append("- RavenCore/").append(path).append('\n');
        }

        return out.toString();
    }

    private static void appendValue(StringBuilder out, String label, Object value) {
        if (value == null) return;
        out.append(label).append(": ").append(value).append('\n');
    }

    private static void appendPlaceholders(StringBuilder out, String title, List<String> placeholders) {
        out.append(title).append('\n');
        for (String placeholder : placeholders) {
            out.append("- ").append(placeholder).append('\n');
        }
    }
}
