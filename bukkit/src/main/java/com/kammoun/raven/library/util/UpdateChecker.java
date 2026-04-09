package com.kammoun.raven.library.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * Checks for updates against the SpigotMC resource page asynchronously.
 *
 * Usage:
 * <pre>
 *   new UpdateChecker(plugin, 12345).check(latestVersion -> {
 *       plugin.getLogger().info("New version available: " + latestVersion);
 *   });
 * </pre>
 */
public class UpdateChecker {

    private final JavaPlugin plugin;
    private final int resourceId;

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    /**
     * Checks for a new version asynchronously in spigot.
     * Calls {@code onNewVersion} with the latest version string only if an update is available.
     */
    public void check(Consumer<String> onNewVersion) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (InputStream stream = new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openStream();
                 Scanner scanner = new Scanner(stream)) {

                if (!scanner.hasNext()) return;

                String latest = scanner.next().trim();
                String current = plugin.getDescription().getVersion().trim();

                if (!current.equals(latest)) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> onNewVersion.accept(latest));
                }

            } catch (IOException e) {
                plugin.getLogger().warning("[UpdateChecker] Could not reach SpigotMC: " + e.getMessage());
            }
        });
    }
}
