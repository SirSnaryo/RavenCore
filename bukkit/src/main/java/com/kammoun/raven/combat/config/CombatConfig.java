package com.kammoun.raven.combat.config;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

public record CombatConfig(int durationSeconds, boolean killOnLogout, boolean disableElytra,
                           boolean disableEnderPearls, boolean allowEnderPearlsInCombat,
                           boolean blockProtectedRegions, boolean ignoreOp,
                           boolean actionBarEnabled, String actionBar, List<String> blockedCommands,
                           String prefix, FileConfiguration source) {
    public static CombatConfig load(FileConfiguration config) {
        return new CombatConfig(Math.max(1, config.getInt("combat.duration", 30)),
                config.getBoolean("combat.kill-on-logout", true),
                config.getBoolean("combat.disable-elytra", true),
                config.getBoolean("combat.disable-enderpearls", true),
                config.getBoolean("combat.allow-enderpearls-in-combat", true),
                config.getBoolean("combat.worldguard-block", true),
                config.getBoolean("combat.ignore-op", false),
                config.getBoolean("combat.actionbar.enabled", true),
                config.getString("combat.actionbar.message", "&c⚔ In combat for &e%time%s"),
                config.getStringList("blocked-commands").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT)).toList(),
                config.getString("messages.prefix", ""), config);
    }

    public boolean blockVanillaEnderPearls() {
        return disableEnderPearls && !allowEnderPearlsInCombat;
    }

    public boolean blocked(String commandLine) {
        String command = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String base = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return blockedCommands.contains(base);
    }

    public String message(String key, String... replacements) {
        String value = prefix + source.getString("messages." + key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i + 1]);
        return ChatFormatter.color(value);
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String value = message(key, replacements);
        if (!value.isBlank()) sender.sendMessage(value);
    }
}
