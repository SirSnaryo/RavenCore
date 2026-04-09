package com.kammoun.raven.library.util;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prefixed logger that wraps a plugin logger and adds colored console output.
 *
 * Usage:
 * <pre>
 *   KLogger log = new KLogger(plugin, "ItemModule");
 *   log.info("Loaded 5 items");
 *   log.console("&aLoaded &e5 &aitems");
 * </pre>
 */
public class KLogger {

    private final Logger logger;
    private final String prefix;

    public KLogger(JavaPlugin plugin, String moduleName) {
        this.logger = plugin.getLogger();
        this.prefix = "[" + moduleName + "] ";
    }

    public KLogger(Logger logger, String moduleName) {
        this.logger = logger;
        this.prefix = "[" + moduleName + "] ";
    }

    public void info(String message) {
        logger.info(prefix + message);
    }

    public void warn(String message) {
        logger.warning(prefix + message);
    }

    public void error(String message) {
        logger.severe(prefix + message);
    }

    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, prefix + message, throwable);
    }

    public void debug(String message) {
        logger.fine(prefix + message);
    }

    /**
     * Sends a color-formatted message directly to the console sender.
     * Supports {@code &} color codes and hex colors ({@code &#RRGGBB}).
     */
    public void console(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatFormatter.color(prefix + message));
    }
}
