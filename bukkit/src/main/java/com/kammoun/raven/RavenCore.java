package com.kammoun.raven;

import com.kammoun.raven.chat.ChatModule;
import com.kammoun.raven.clearinventory.ClearInventoryModule;
import com.kammoun.raven.economy.EconomyModule;
import com.kammoun.raven.lastlocation.LastLocationModule;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.portal.DimensionPortalModule;
import com.kammoun.raven.portal.border.ServerBorderModule;
import com.kammoun.raven.rtp.RtpModule;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.scoreboard.ScoreboardModule;
import com.kammoun.raven.spawner.SpawnerModule;
import com.kammoun.raven.teleport.TeleportModule;
import com.kammoun.raven.spawn.SpawnModule;
import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.library.redis.RedisCredentials;
import com.kammoun.raven.library.redis.RedisMessenger;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class RavenCore extends JavaPlugin {

    @Getter
    private static RavenCore instance;

    private final List<String> enabledModules = new ArrayList<>();

    private String serverId;
    private ServerRole serverRole;
    private RedisCredentials redisCredentials;
    private RedisMessenger redisMessenger;
    private NetworkEventBus networkEventBus;
    private NetworkPlayerManager networkPlayerManager;
    private MySQLDBManager mySqlManager;
    private LastLocationModule lastLocationModule;
    private EconomyModule economyModule;
    private ClearInventoryModule clearInventoryModule;
    private DimensionPortalModule dimensionPortalModule;
    private ServerBorderModule serverBorderModule;
    private TeleportModule teleportModule;
    private SpawnModule spawnModule;
    private RtpModule rtpModule;
    private ShopModule shopModule;
    private SpawnerModule spawnerModule;
    private ChatModule chatModule;
    private ScoreboardModule scoreboardModule;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();
        instance = this;
        saveDefaultConfig();
        SchedulerUtil.init(this);
        registerMenuClickDispatch();

        String configuredId = getConfig().getString("network.server-id", "").trim();
        serverId   = configuredId.isEmpty() ? getServer().getName() : configuredId;
        serverRole = ServerRole.parse(getConfig().getString("server-role", "overworld"));

        printBanner();

        if (serverRole == ServerRole.SURVIVAL) {
            enforceWorldSeed();
        }

        if (serverRole != ServerRole.SURVIVAL && serverRole != ServerRole.SPAWN) {
            enforceWorldRole();
        }

        if (serverRole == ServerRole.SPAWN) {
            blockPortals();
        }

        redisCredentials = new RedisCredentials(
                getConfig().getString("redis.host", "127.0.0.1"),
                getConfig().getInt("redis.port", 6379),
                getConfig().getString("redis.password", null),
                getConfig().getInt("redis.database", 0)
        );

        redisMessenger = new RedisMessenger(getLogger());
        redisMessenger.connect(redisCredentials);
        if (redisMessenger.isConnected()) {
            getLogger().info("Redis connected (" + redisCredentials.getHost() + ":" + redisCredentials.getPort() + ").");
        } else {
            getLogger().warning("Redis connection failed (" + redisCredentials.getHost() + ":" + redisCredentials.getPort()
                    + "). Network features will be degraded.");
        }

        networkEventBus = new NetworkEventBus(redisMessenger, serverId, SchedulerUtil::run);
        networkPlayerManager = new NetworkPlayerManager(this, redisMessenger.getConnection().getPool(), serverId, networkEventBus);

        mySqlManager = new MySQLDBManager(getConfig());
        mySqlManager.connect();
        if (mySqlManager.isConnected()) {
            getLogger().info("MySQL connection pool ready.");
        } else {
            getLogger().warning("MySQL connection failed. Persistent data will not be saved.");
        }

        if (serverRole != ServerRole.SPAWN) {
            lastLocationModule = new LastLocationModule();
            lastLocationModule.onEnable(this);
            enabledModules.add("LastLocation");
        }

        economyModule = new EconomyModule();
        economyModule.onEnable(this);
        enabledModules.add("Economy");

        shopModule = new ShopModule();
        shopModule.onEnable(this);
        enabledModules.add("Shop");

        spawnerModule = new SpawnerModule();
        spawnerModule.onEnable(this);
        enabledModules.add("Spawner");

        clearInventoryModule = new ClearInventoryModule();
        clearInventoryModule.onEnable(this);
        enabledModules.add("ClearInventory");

        chatModule = new ChatModule();
        chatModule.onEnable(this);
        enabledModules.add("Chat");

        scoreboardModule = new ScoreboardModule();
        scoreboardModule.onEnable(this);
        enabledModules.add("Scoreboard");

        teleportModule = new TeleportModule();
        teleportModule.onEnable(this);
        enabledModules.add("Teleport");

        spawnModule = new SpawnModule();
        spawnModule.onEnable(this);
        enabledModules.add("Spawn");

        if (serverRole != ServerRole.SPAWN) {
            dimensionPortalModule = new DimensionPortalModule();
            dimensionPortalModule.onEnable(this);
            enabledModules.add("DimensionPortal");
        }

        // RTP is available on every server role: on a lobby it hands the player off to a
        // gameplay server, on a gameplay server it teleports locally — so it always enables.
        rtpModule = new RtpModule();
        rtpModule.onEnable(this);
        enabledModules.add("RTP");

        if (serverRole == ServerRole.SURVIVAL) {
            serverBorderModule = new ServerBorderModule();
            serverBorderModule.onEnable(this);
            enabledModules.add("ServerBorder");
        }

        printReady(System.currentTimeMillis() - startedAt);
    }

    /** Console banner shown at the very start of enable, before modules load. */
    private void printBanner() {
        var console = Bukkit.getConsoleSender();
        console.sendMessage(ChatFormatter.color("&8&m                                        "));
        console.sendMessage(ChatFormatter.color("&5&lRaven&d&lCore &7v" + getDescription().getVersion()));
        console.sendMessage(ChatFormatter.color("&7Server ID: &f" + serverId + " &8| &7Role: &f" + serverRole));
        console.sendMessage(ChatFormatter.color("&7Starting up..."));
        console.sendMessage(ChatFormatter.color("&8&m                                        "));
    }

    /** Console summary shown once every module has finished loading. */
    private void printReady(long elapsedMs) {
        var console = Bukkit.getConsoleSender();
        console.sendMessage(ChatFormatter.color("&8&m                                        "));
        console.sendMessage(ChatFormatter.color("&a&lRavenCore enabled &7in &f" + elapsedMs + "ms"));
        console.sendMessage(ChatFormatter.color("&7Modules (&f" + enabledModules.size() + "&7): &f"
                + String.join("&7, &f", enabledModules)));
        console.sendMessage(ChatFormatter.color("&8&m                                        "));
    }

    /**
     * Single, plugin-wide dispatcher from raw inventory clicks to {@link KMenu#handleClick}.
     * Any module that opens a KMenu-backed GUI (spawn/afk, rtp, ...) shares this one listener
     * instead of each registering its own — registering it per-module would double-cancel and
     * double-handle every click once more than one menu-based feature is active.
     */
    private void registerMenuClickDispatch() {
        Bukkit.getPluginManager().registerEvent(
                InventoryClickEvent.class, new Listener() {}, EventPriority.NORMAL,
                (l, e) -> {
                    InventoryClickEvent event = (InventoryClickEvent) e;
                    if (!(event.getInventory().getHolder() instanceof KMenu menu)) return;
                    event.setCancelled(true);
                    if (event.getClickedInventory() == null
                            || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
                    menu.handleClick(event);
                }, this
        );
    }

    /** Lobby servers have no gameplay dimensions; cancel any portal use outright. */
    private void blockPortals() {
        Bukkit.getPluginManager().registerEvent(
                PlayerPortalEvent.class, new Listener() {}, EventPriority.LOWEST,
                (l, e) -> ((PlayerPortalEvent) e).setCancelled(true), this
        );
    }

    /**
     * All regional overworld servers share one world seed so terrain lines up across
     * the borders drawn by {@link com.kammoun.raven.portal.border.ServerBorderModule}. Plugins
     * load with {@code load: STARTUP}, so no world is guaranteed loaded yet — check any
     * world that's already up and also listen for the overworld's WorldLoadEvent.
     */
    private void enforceWorldSeed() {
        String expected = getConfig().getString("network.expected-seed", "").trim();
        if (expected.isEmpty()) return;

        long expectedSeed;
        try {
            expectedSeed = Long.parseLong(expected);
        } catch (NumberFormatException e) {
            getLogger().severe("[Seed] network.expected-seed ('" + expected + "') is not a valid number.");
            haltServer();
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                checkWorldSeed(world, expectedSeed);
                return;
            }
        }

        Bukkit.getPluginManager().registerEvent(
                WorldLoadEvent.class, new Listener() {}, EventPriority.MONITOR,
                (l, e) -> {
                    World world = ((WorldLoadEvent) e).getWorld();
                    if (world.getEnvironment() == World.Environment.NORMAL) {
                        checkWorldSeed(world, expectedSeed);
                    }
                }, this
        );
    }

    private void checkWorldSeed(World world, long expectedSeed) {
        long actualSeed = world.getSeed();
        if (actualSeed == expectedSeed) return;

        getLogger().severe("[Seed] World seed mismatch: world '" + world.getName() + "' has seed "
                + actualSeed + " but network.expected-seed is " + expectedSeed + ". All regional "
                + "servers must share the same seed or terrain won't line up across borders.");
        haltServer();
    }

    private void haltServer() {
        getLogger().severe("[Seed] Shutting down to avoid running with mismatched terrain.");
        Bukkit.shutdown();
    }

    private void enforceWorldRole() {
        // Unload any already-loaded worlds that don't match the role
        for (World world : Bukkit.getWorlds()) {
            if (!serverRole.allows(world.getEnvironment())) {
                unloadForRole(world, "Unloaded world '" + world.getName() + "' (not needed for role " + serverRole + ")");
            }
        }
        // Block any future world loads that don't match
        Bukkit.getPluginManager().registerEvent(
                WorldLoadEvent.class, new Listener() {}, EventPriority.LOWEST,
                (l, e) -> {
                    World w = ((WorldLoadEvent) e).getWorld();
                    if (!serverRole.allows(w.getEnvironment())) {
                        unloadForRole(w, "Blocked load of world '" + w.getName() + "'");
                    }
                }, this
        );
    }

    /**
     * Unloading can't happen while a world is still being initialised — during the server's
     * initial world load (or from inside WorldLoadEvent) {@link Bukkit#unloadWorld} throws
     * UnsupportedOperationException on Folia/Canvas. Defer it to the global region scheduler so
     * the unload runs once the world has finished loading and the server is ticking.
     */
    private void unloadForRole(World world, String logMessage) {
        Bukkit.getGlobalRegionScheduler().run(this, task -> {
            if (Bukkit.unloadWorld(world, false)) {
                getLogger().info("[Role] " + logMessage);
            } else {
                getLogger().warning("[Role] Failed to unload world '" + world.getName() + "' for role " + serverRole);
            }
        });
    }

    @Override
    public void onDisable() {
        if (spawnModule != null) spawnModule.onDisable(this);
        if (rtpModule != null) rtpModule.onDisable(this);
        if (teleportModule != null) teleportModule.onDisable(this);
        if (serverBorderModule != null) {
            serverBorderModule.onDisable(this);
        }
        if (dimensionPortalModule != null) {
            dimensionPortalModule.onDisable(this);
        }
        if (clearInventoryModule != null) {
            clearInventoryModule.onDisable(this);
        }
        if (chatModule != null) {
            chatModule.onDisable(this);
        }
        if (scoreboardModule != null) {
            scoreboardModule.onDisable(this);
        }
        if (shopModule != null) {
            shopModule.onDisable(this);
        }
        if (spawnerModule != null) {
            spawnerModule.onDisable(this);
        }
        if (economyModule != null) {
            economyModule.onDisable(this);
        }
        if (lastLocationModule != null) {
            lastLocationModule.onDisable(this);
        }
        if (redisMessenger != null) {
            redisMessenger.close();
        }
        if (mySqlManager != null) {
            mySqlManager.disconnect();
        }
    }

}
