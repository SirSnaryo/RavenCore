package com.kammoun.raven.library.platform.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Base class for PlaceholderAPI expansions with a simple registration DSL.
 *
 * Requires PlaceholderAPI as a soft dependency in your plugin.yml:
 * <pre>
 *   softdepend: [PlaceholderAPI]
 * </pre>
 *
 * Usage:
 * <pre>
 *   public class MyExpansion extends KPlaceholderExpansion {
 *       public MyExpansion(JavaPlugin plugin) {
 *           super(plugin, "myplugin");
 *           register("balance", player -> String.valueOf(getBalance(player)));
 *           register("rank", player -> getRank(player));
 *       }
 *   }
 *
 *   // Register on enable (after checking PAPI is present):
 *   if (KPlaceholderExpansion.isAvailable()) {
 *       new MyExpansion(plugin).register();
 *   }
 * </pre>
 *
 * Placeholders are accessed as: %myplugin_balance%, %myplugin_rank%
 */
public abstract class KPlaceholderExpansion extends PlaceholderExpansion {

    protected final JavaPlugin plugin;
    private final String identifier;
    private final Map<String, Function<OfflinePlayer, String>> handlers = new HashMap<>();
    private final Map<String, BiFunction<OfflinePlayer, String, String>> prefixHandlers = new LinkedHashMap<>();

    protected KPlaceholderExpansion(JavaPlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    /**
     * Registers a placeholder handler for the given key.
     * The resulting placeholder is {@code %<identifier>_<key>%}.
     */
    protected void register(String key, Function<OfflinePlayer, String> handler) {
        handlers.put(key.toLowerCase(), handler);
    }

    /**
     * Registers a handler for every placeholder starting with the given prefix.
     * The handler receives the remainder after the prefix, so
     * {@code registerPrefix("keys_", ...)} answers {@code %<identifier>_keys_<anything>%}.
     * Exact keys registered with {@link #register} always win over prefixes.
     */
    protected void registerPrefix(String prefix, BiFunction<OfflinePlayer, String, String> handler) {
        prefixHandlers.put(prefix.toLowerCase(), handler);
    }

    @Override
    public final String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase();
        Function<OfflinePlayer, String> handler = handlers.get(key);
        if (handler != null) return handler.apply(player);
        for (Map.Entry<String, BiFunction<OfflinePlayer, String, String>> entry : prefixHandlers.entrySet()) {
            if (key.startsWith(entry.getKey())) {
                return entry.getValue().apply(player, key.substring(entry.getKey().length()));
            }
        }
        return null;
    }

    @Override
    public final @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * Returns true if PlaceholderAPI is installed and enabled on this server.
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }
}
