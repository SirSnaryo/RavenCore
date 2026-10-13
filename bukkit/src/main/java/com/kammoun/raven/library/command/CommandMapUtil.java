package com.kammoun.raven.library.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Registers a plugin.yml command straight into the server command map so Raven wins the label even
 * when another plugin registered the same name first.
 */
public final class CommandMapUtil {

    private CommandMapUtil() {
    }

    public static boolean register(JavaPlugin plugin, String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) return false;

        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            Object map = commandMapField.get(Bukkit.getServer());
            if (map instanceof CommandMap commandMap) {
                commandMap.register(plugin.getDescription().getName(), command);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[RavenCore] Could not override the '" + name
                    + "' command from another plugin: " + e.getMessage());
        }
        return false;
    }
}
