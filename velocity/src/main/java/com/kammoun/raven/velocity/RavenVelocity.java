package com.kammoun.raven.velocity;

import com.google.inject.Inject;
import com.kammoun.raven.library.redis.RedisCredentials;
import com.kammoun.raven.library.redis.RedisMessenger;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.network.VelocityNetworkPlayerManager;
import com.kammoun.raven.velocity.info.InfoModule;
import com.kammoun.raven.velocity.lobby.LobbyModule;
import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import com.kammoun.raven.velocity.proxycommandfilter.ProxyCommandFilterModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Plugin(
        id = "ravencore",
        name = "RavenCore",
        version = "1.0.8",
        description = "RavenCore - Velocity",
        authors = {"Kammoun"}
)
@Getter
public class RavenVelocity {

    @Getter
    private static RavenVelocity instance;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private Map<String, Object> config = new HashMap<>();

    private RedisMessenger redisMessenger;
    private NetworkEventBus networkEventBus;
    private VelocityNetworkPlayerManager networkPlayerManager;
    private InfoModule infoModule;
    private ProxyCommandFilterModule proxyCommandFilterModule;
    private PrivateMessageModule privateMessageModule;
    private LobbyModule lobbyModule;

    @Inject
    public RavenVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;

        saveDefaultConfig();
        loadConfig();

        String serverId = getString("network.server-id", "velocity-proxy");

        redisMessenger = new RedisMessenger(logger);
        RedisCredentials credentials = new RedisCredentials(
                getString("redis.host", "127.0.0.1"),
                getInt("redis.port", 6379),
                getString("redis.password", null),
                getInt("redis.database", 0)
        );
        redisMessenger.connect(credentials);

        // Velocity has no single main thread — dispatch events on the calling thread
        networkEventBus = new NetworkEventBus(redisMessenger, serverId, Runnable::run);

        networkPlayerManager = new VelocityNetworkPlayerManager(
                this, server, redisMessenger.getConnection().getPool(), serverId, networkEventBus
        );

        infoModule = new InfoModule();
        infoModule.onEnable(this, server, dataDirectory);

        proxyCommandFilterModule = new ProxyCommandFilterModule();
        proxyCommandFilterModule.onEnable(this, server, dataDirectory);

        privateMessageModule = new PrivateMessageModule();
        privateMessageModule.onEnable(this, server, dataDirectory);

        lobbyModule = new LobbyModule();
        lobbyModule.onEnable(this, server, dataDirectory);

        logger.info("RavenCore (Velocity) enabled — proxy id: " + serverId);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (infoModule != null) {
            infoModule.onDisable();
        }
        if (proxyCommandFilterModule != null) {
            proxyCommandFilterModule.onDisable();
        }
        if (privateMessageModule != null) {
            privateMessageModule.onDisable();
        }
        if (lobbyModule != null) {
            lobbyModule.onDisable();
        }
        if (redisMessenger != null) {
            redisMessenger.close();
        }
    }

    // -- Config helpers --

    private void saveDefaultConfig() {
        File configFile = dataDirectory.resolve("config.yml").toFile();
        if (configFile.exists()) return;

        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.warning("Failed to create data directory: " + e.getMessage());
            return;
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("velocity-config.yml")) {
            if (in == null) return;
            Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("Failed to save default config: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        File configFile = dataDirectory.resolve("config.yml").toFile();
        try (InputStream in = new FileInputStream(configFile)) {
            Map<String, Object> loaded = new Yaml().load(in);
            this.config = loaded != null ? loaded : new HashMap<>();
        } catch (IOException e) {
            this.config = new HashMap<>();
        }
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
