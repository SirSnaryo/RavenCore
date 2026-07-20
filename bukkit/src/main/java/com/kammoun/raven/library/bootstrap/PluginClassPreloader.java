package com.kammoun.raven.library.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URI;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class PluginClassPreloader {

    private static final String PACKAGE_PREFIX = "com/kammoun/raven/";
    private static final String LIBS_PREFIX = "com/kammoun/raven/libs/";

    private PluginClassPreloader() {}

    public static void preload(JavaPlugin plugin) {
        ClassLoader loader = plugin.getClass().getClassLoader();
        int loaded = 0;
        int skipped = 0;

        try {
            URI location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            File file = new File(location);
            if (!file.isFile()) {
                return;
            }

            try (JarFile jar = new JarFile(file)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!isPluginClass(name)) {
                        continue;
                    }

                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    try {
                        Class.forName(className, false, loader);
                        loaded++;
                    } catch (LinkageError | ClassNotFoundException e) {
                        skipped++;
                        plugin.getLogger().fine("[Bootstrap] Skipped preload for " + className + ": " + e.getMessage());
                    }
                }
            }

            plugin.getLogger().info("[Bootstrap] Preloaded " + loaded + " RavenCore class(es)"
                    + (skipped > 0 ? " (" + skipped + " skipped)" : "") + ".");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Bootstrap] Failed to preload plugin classes.", e);
        }
    }

    private static boolean isPluginClass(String name) {
        return name.startsWith(PACKAGE_PREFIX)
                && !name.startsWith(LIBS_PREFIX)
                && name.endsWith(".class")
                && !name.equals(PACKAGE_PREFIX + "RavenCore.class");
    }
}
