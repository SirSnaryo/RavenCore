package com.kammoun.raven;

import com.kammoun.raven.clearinventory.ClearInventoryModule;
import com.kammoun.raven.economy.EconomyModule;
import com.kammoun.raven.lastlocation.LastLocationModule;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.portal.DimensionPortalModule;
import com.kammoun.raven.portal.border.ServerBorderModule;
import com.kammoun.raven.rtp.RtpModule;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.spawner.SpawnerModule;
import com.kammoun.raven.teleport.TeleportModule;
import com.kammoun.raven.spawn.SpawnModule;
import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.library.redis.RedisCredentials;
import com.kammoun.raven.library.redis.RedisMessenger;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
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

@Getter
public final class RavenCore extends JavaPlugin {

    @Getter
    private static RavenCore instance;

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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        SchedulerUtil.init(this);
        registerMenuClickDispatch();

        String configuredId = getConfig().getString("network.server-id", "").trim();
        serverId   = configuredId.isEmpty() ? getServer().getName() : configuredId;
        serverRole = ServerRole.parse(getConfig().getString("server-role", "overworld"));

        if (serverRole == ServerRole.OVERWORLD) {
            enforceWorldSeed();
        }

        if (serverRole != ServerRole.OVERWORLD && serverRole != ServerRole.SPAWN) {
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

        networkEventBus = new NetworkEventBus(redisMessenger, serverId, SchedulerUtil::run);
        networkPlayerManager = new NetworkPlayerManager(this, redisMessenger.getConnection().getPool(), serverId, networkEventBus);

        mySqlManager = new MySQLDBManager(getConfig());
        mySqlManager.connect();

        if (serverRole != ServerRole.SPAWN) {
            lastLocationModule = new LastLocationModule();
            lastLocationModule.onEnable(this);
        }

        economyModule = new EconomyModule();
        economyModule.onEnable(this);

        shopModule = new ShopModule();
        shopModule.onEnable(this);

        spawnerModule = new SpawnerModule();
        spawnerModule.onEnable(this);

        clearInventoryModule = new ClearInventoryModule();
        clearInventoryModule.onEnable(this);

        teleportModule = new TeleportModule();
        teleportModule.onEnable(this);

        spawnModule = new SpawnModule();
        spawnModule.onEnable(this);

        if (serverRole != ServerRole.SPAWN) {
            dimensionPortalModule = new DimensionPortalModule();
            dimensionPortalModule.onEnable(this);

            rtpModule = new RtpModule();
            rtpModule.onEnable(this);
        }

        if (serverRole == ServerRole.OVERWORLD) {
            serverBorderModule = new ServerBorderModule();
            serverBorderModule.onEnable(this);
        }
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
                Bukkit.unloadWorld(world, false);
                getLogger().info("[Role] Unloaded world '" + world.getName() + "' (not needed for role " + serverRole + ")");
            }
        }
        // Block any future world loads that don't match
        Bukkit.getPluginManager().registerEvent(
                WorldLoadEvent.class, new Listener() {}, EventPriority.LOWEST,
                (l, e) -> {
                    World w = ((WorldLoadEvent) e).getWorld();
                    if (!serverRole.allows(w.getEnvironment())) {
                        Bukkit.unloadWorld(w, false);
                        getLogger().info("[Role] Blocked load of world '" + w.getName() + "'");
                    }
                }, this
        );
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
