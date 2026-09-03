package com.kammoun.raven.velocity;

import com.google.inject.Inject;
import com.kammoun.raven.library.redis.RedisCredentials;
import com.kammoun.raven.library.redis.RedisMessenger;
import com.kammoun.raven.library.redis.event.CrossServerTeleportEvent;
import com.kammoun.raven.library.redis.event.CombatTagEvent;
import com.kammoun.raven.library.redis.event.CombatUntagEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.network.VelocityNetworkPlayerManager;
import com.kammoun.raven.velocity.command.RavenCoreVelocityCommand;
import com.kammoun.raven.velocity.info.InfoModule;
import com.kammoun.raven.velocity.borders.BorderTransferModule;
import com.kammoun.raven.velocity.lobby.LobbyModule;
import com.kammoun.raven.velocity.privatemessage.PrivateMessageModule;
import com.kammoun.raven.velocity.proxycommandfilter.ProxyCommandFilterModule;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Plugin(
        id = "ravencore",
        name = "RavenCore",
        version = "2.6",
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
    private BorderTransferModule borderTransferModule;
    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> internalTransfers = new ConcurrentHashMap<>();

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
        networkEventBus.subscribe(CrossServerTeleportEvent.class, this::onTeleportTransfer);
        networkEventBus.subscribe(CombatTagEvent.class, this::onCombatTag);
        networkEventBus.subscribe(CombatUntagEvent.class, this::onCombatUntag);

        infoModule = new InfoModule();
        infoModule.onEnable(this, server, dataDirectory);

        proxyCommandFilterModule = new ProxyCommandFilterModule();
        proxyCommandFilterModule.onEnable(this, server, dataDirectory);

        privateMessageModule = new PrivateMessageModule(this);
        privateMessageModule.onEnable(this, server, dataDirectory);

        lobbyModule = new LobbyModule();
        lobbyModule.onEnable(this, server, dataDirectory);

        borderTransferModule = new BorderTransferModule(this);
        borderTransferModule.onEnable(this, server, dataDirectory);
        registerCoreCommand();

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
        if (borderTransferModule != null) {
            borderTransferModule.onDisable();
        }
        if (networkEventBus != null) {
            networkEventBus.unsubscribe(CrossServerTeleportEvent.class);
            networkEventBus.unsubscribe(CombatTagEvent.class);
            networkEventBus.unsubscribe(CombatUntagEvent.class);
        }
        if (redisMessenger != null) {
            redisMessenger.close();
        }
    }

    private void onTeleportTransfer(CrossServerTeleportEvent event) {
        if (event == null || event.getPlayerUuid() == null || event.getTargetServer() == null) {
            return;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(event.getPlayerUuid());
        } catch (IllegalArgumentException ignored) {
            logger.warning("[Teleport] Invalid transfer UUID: " + event.getPlayerUuid());
            return;
        }
        server.getScheduler().buildTask(this, () ->
                server.getPlayer(playerUuid).ifPresent(player -> server.getServer(event.getTargetServer()).ifPresentOrElse(
                        target -> {
                            markInternalTransfer(playerUuid, 15_000L);
                            player.createConnectionRequest(target).connect()
                                .thenAccept(result -> {
                                    if (!result.isSuccessful()) {
                                        logger.warning("[Teleport] Failed to send " + player.getUsername()
                                                + " to " + event.getTargetServer() + ": " + result.getStatus());
                                    }
                                });
                        },
                        () -> logger.warning("[Teleport] Unknown target server: " + event.getTargetServer())
                ))
        ).schedule();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (event.getPlayer().getCurrentServer().isEmpty() || !isInCombat(event.getPlayer().getUniqueId())) return;
        if (isInternalTransfer(event.getPlayer().getUniqueId())) return;
        String current = event.getPlayer().getCurrentServer().get().getServerInfo().getName();
        if (current.equalsIgnoreCase(event.getOriginalServer().getServerInfo().getName())) return;
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        event.getPlayer().sendMessage(Component.text(
                "You cannot change servers while in combat!", NamedTextColor.RED));
    }

    private void onCombatTag(CombatTagEvent event) {
        try {
            UUID uuid = UUID.fromString(event.getPlayerUuid());
            if (event.getExpiresAt() > System.currentTimeMillis()) combatTags.put(uuid, event.getExpiresAt());
        } catch (RuntimeException ignored) {
        }
    }

    private void onCombatUntag(CombatUntagEvent event) {
        try {
            combatTags.remove(UUID.fromString(event.getPlayerUuid()));
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isInCombat(UUID uuid) {
        Long expiry = combatTags.get(uuid);
        if (expiry == null) return false;
        if (expiry > System.currentTimeMillis()) return true;
        combatTags.remove(uuid, expiry);
        return false;
    }

    public void markInternalTransfer(UUID uuid, long durationMillis) {
        internalTransfers.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    private boolean isInternalTransfer(UUID uuid) {
        Long expiry = internalTransfers.get(uuid);
        if (expiry == null) return false;
        if (expiry > System.currentTimeMillis()) return true;
        internalTransfers.remove(uuid, expiry);
        return false;
    }

    public String getVersion() {
        Plugin plugin = getClass().getAnnotation(Plugin.class);
        return plugin == null ? "unknown" : plugin.version();
    }

    public List<String> reloadableModuleNames() {
        return new ArrayList<>(reloadableModules().keySet());
    }

    public boolean reloadModuleSafely(String name) {
        Runnable reload = reloadableModules().get(name.toLowerCase());
        if (reload == null) {
            return false;
        }

        server.getScheduler().buildTask(this, () -> {
            try {
                reload.run();
                logger.info("[RavenCoreVelocity] Reloaded module: " + name);
            } catch (Exception e) {
                logger.log(java.util.logging.Level.SEVERE, "[RavenCoreVelocity] Failed to reload module: " + name, e);
            }
        }).schedule();
        return true;
    }

    public void reloadAllModulesSafely() {
        reloadableModuleNames().forEach(this::reloadModuleSafely);
    }

    private Map<String, Runnable> reloadableModules() {
        Map<String, Runnable> modules = new LinkedHashMap<>();
        modules.put("core", this::reloadRootConfig);
        if (infoModule != null) modules.put("info", infoModule::reload);
        if (proxyCommandFilterModule != null) modules.put("proxycommandfilter", proxyCommandFilterModule::reload);
        if (proxyCommandFilterModule != null) modules.put("antitab", proxyCommandFilterModule::reload);
        if (proxyCommandFilterModule != null) modules.put("tab", proxyCommandFilterModule::reload);
        if (privateMessageModule != null) modules.put("privatemessage", privateMessageModule::reload);
        if (lobbyModule != null) modules.put("lobby", lobbyModule::reload);
        if (borderTransferModule != null) modules.put("borders", borderTransferModule::reload);
        return modules;
    }

    private void registerCoreCommand() {
        var manager = server.getCommandManager();
        manager.register(
                manager.metaBuilder("ravencorevelo")
                        .aliases("rcvelo")
                        .plugin(this)
                        .build(),
                new RavenCoreVelocityCommand(this)
        );
    }

    private void reloadRootConfig() {
        saveDefaultConfig();
        loadConfig();
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
