package com.kammoun.raven.library.module;

import com.velocitypowered.api.proxy.ProxyServer;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for Velocity modules — the Velocity equivalent of {@link com.kammoun.raven.library.module.KModule}.
 * <p>
 * Each module owns a sub-folder inside the plugin's data directory and gets its own {@code config.yml}.
 * Config is loaded with snakeyaml (provided by both Paper and Velocity at runtime).
 */
public abstract class VModule {

    private final String moduleName;
    protected Object plugin;
    protected ProxyServer server;
    protected File moduleDataFolder;
    private Map<String, Object> config = new HashMap<>();

    public VModule(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("Module name cannot be null or empty");
        }
        this.moduleName = moduleName;
    }

    public final void onEnable(Object plugin, ProxyServer server, Path pluginDataDir) {
        this.plugin = plugin;
        this.server = server;
        this.moduleDataFolder = pluginDataDir.resolve(moduleName).toFile();

        if (!moduleDataFolder.exists() && !moduleDataFolder.mkdirs()) {
            getLogger().warning("Failed to create module data folder: " + moduleDataFolder.getPath());
        }

        saveDefaultConfig("config.yml");
        loadConfig();

        try {
            onModuleEnable();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error enabling module " + moduleName, e);
        }
    }

    public final void onDisable() {
        try {
            onModuleDisable();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error disabling module " + moduleName, e);
        }
    }

    public abstract void onModuleEnable();

    public abstract void onModuleDisable();

    public String getName() {
        return moduleName;
    }

    public Logger getLogger() {
        return Logger.getLogger("RavenCore/" + moduleName);
    }

    public void registerListener(Object listener) {
        server.getEventManager().register(plugin, listener);
    }

    // -- Config --

    public void saveDefaultConfig(String configName) {
        File configFile = new File(moduleDataFolder, configName);
        if (configFile.exists()) return;

        String resourcePath = moduleName + "/" + configName;
        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                getLogger().warning("Could not find default config: " + resourcePath);
                return;
            }
            Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save default config: " + configName, e);
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public String getString(String path, String def) {
        Object val = getNestedValue(path);
        return val != null ? val.toString() : def;
    }

    public int getInt(String path, int def) {
        Object val = getNestedValue(path);
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object val = getNestedValue(path);
        if (val instanceof Boolean b) return b;
        return def;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object val = getNestedValue(path);
        if (!(val instanceof List<?> list)) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (Object o : list) {
            if (o != null) result.add(o.toString());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        File configFile = new File(moduleDataFolder, "config.yml");
        try (InputStream in = new FileInputStream(configFile)) {
            Map<String, Object> loaded = new Yaml().load(in);
            this.config = loaded != null ? loaded : new HashMap<>();
        } catch (IOException e) {
            this.config = new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(String path) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = config;
        for (int i = 0; i < keys.length - 1; i++) {
            Object next = current.get(keys[i]);
            if (!(next instanceof Map)) return null;
            current = (Map<String, Object>) next;
        }
        return current.get(keys[keys.length - 1]);
    }
}
