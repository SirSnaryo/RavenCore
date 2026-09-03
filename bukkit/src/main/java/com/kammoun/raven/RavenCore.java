package com.kammoun.raven;

import com.kammoun.raven.billford.BillfordModule;
import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.endportal.EndPortalModule;
import com.kammoun.raven.chat.ChatModule;
import com.kammoun.raven.combat.CombatModule;
import com.kammoun.raven.clearinventory.ClearInventoryModule;
import com.kammoun.raven.nightvision.NightVisionModule;
import com.kammoun.raven.command.RavenCoreCommand;
import com.kammoun.raven.crates.CratesModule;
import com.kammoun.raven.docs.DocsGenerator;
import com.kammoun.raven.economy.EconomyModule;
import com.kammoun.raven.globalcontrol.GlobalModule;
import com.kammoun.raven.home.HomeModule;
import com.kammoun.raven.joinfix.JoinFixModule;
import com.kammoun.raven.lastlocation.LastLocationModule;
import com.kammoun.raven.leaderboard.LeaderboardModule;
import com.kammoun.raven.locationPool.LocationPoolModule;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.library.bootstrap.PluginClassPreloader;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.rtpzone.RtpZoneModule;
import com.kammoun.raven.rtp.RtpModule;
import com.kammoun.raven.pearlstasis.PearlStasisModule;
import com.kammoun.raven.respawn.RespawnModule;
import com.kammoun.raven.scoreboard.ScoreboardModule;
import com.kammoun.raven.serverinfo.ServerInfoModule;
import com.kammoun.raven.shop.ShopModule;
import com.kammoun.raven.settings.SettingsModule;
import com.kammoun.raven.tpa.TeleportModule;
import com.kammoun.raven.team.TeamModule;
import com.kammoun.raven.time.TimeSyncModule;
import com.kammoun.raven.spawn.SpawnModule;
import com.kammoun.raven.stats.StatsModule;
import com.kammoun.raven.library.database.MySQLDBManager;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.library.redis.RedisCredentials;
import com.kammoun.raven.library.redis.RedisMessenger;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.skin.api.SkinCacheAPI;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Getter
public final class RavenCore extends JavaPlugin {

    @Getter
    private static RavenCore instance;

    private final List<String> enabledModules = new ArrayList<>();

    private String serverId;
    private String serverDisplayName;
    private ServerRole serverRole;
    private RedisCredentials redisCredentials;
    private RedisMessenger redisMessenger;
    private NetworkEventBus networkEventBus;
    private NetworkPlayerManager networkPlayerManager;
    private MySQLDBManager mySqlManager;
    private LastLocationModule lastLocationModule;
    private LocationPoolModule locationPoolModule;
    private EconomyModule economyModule;
    private ClearInventoryModule clearInventoryModule;
    private NightVisionModule nightVisionModule;
    private TimeSyncModule timeSyncModule;
    private TeleportModule teleportModule;
    private SpawnModule spawnModule;
    private RtpZoneModule rtpZoneModule;
    private RtpModule rtpModule;
    private RespawnModule respawnModule;
    private HomeModule homeModule;
    private TeamModule teamModule;
    private CombatModule combatModule;
    private ShopModule shopModule;
    private MythicToolsModule mythicToolsModule;
    private GlobalModule globalModule;
    private ChatModule chatModule;
    private SettingsModule settingsModule;
    private ServerInfoModule serverInfoModule;
    private ScoreboardModule scoreboardModule;
    private BillfordModule billfordModule;
    private StatsModule statsModule;
    private LeaderboardModule leaderboardModule;
    private BorderModule borderModule;
    private PearlStasisModule pearlStasisModule;
    private EndPortalModule endPortalModule;
    private CratesModule cratesModule;
    private JoinFixModule joinFixModule;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        SchedulerUtil.init(this);
        PluginClassPreloader.preload(this);
        registerMenuClickDispatch();

        String configuredId = getConfig().getString("network.server-id", "").trim();
        serverId   = configuredId.isEmpty() ? getServer().getName() : configuredId;
        String configuredDisplayName = getConfig().getString("network.server-display-name", "").trim();
        serverDisplayName = configuredDisplayName.isEmpty() ? serverId : configuredDisplayName;
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
        networkPlayerManager = new NetworkPlayerManager(this, redisMessenger.getConnection().getPool(),
                serverId, serverDisplayName, networkEventBus);

        serverInfoModule = new ServerInfoModule();
        serverInfoModule.onEnable(this);
        enabledModules.add("ServerInfo");

        mySqlManager = new MySQLDBManager(getConfig());
        mySqlManager.connect();
        if (mySqlManager.isConnected()) {
            getLogger().info("MySQL connection pool ready.");
        } else {
            getLogger().warning("MySQL connection failed. Persistent data will not be saved.");
        }

        initializeSkinCache();

        locationPoolModule = new LocationPoolModule();
        locationPoolModule.onEnable(this);
        enabledModules.add("LocationPool");

        rtpModule = new RtpModule();
        rtpModule.onEnable(this);
        enabledModules.add("RTP");

        respawnModule = new RespawnModule();
        respawnModule.onEnable(this);
        enabledModules.add("Respawn");

        if (serverRole == ServerRole.SURVIVAL || serverRole == ServerRole.END) {
            joinFixModule = new JoinFixModule();
            joinFixModule.onEnable(this);
            enabledModules.add("JoinFix");
        }

        homeModule = new HomeModule();
        homeModule.onEnable(this);
        enabledModules.add("Home");

        teamModule = new TeamModule();
        teamModule.onEnable(this);
        enabledModules.add("Team");

        if (serverRole != ServerRole.SPAWN) {
            combatModule = new CombatModule();
            combatModule.onEnable(this);
            enabledModules.add("Combat");
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

        mythicToolsModule = new MythicToolsModule();
        mythicToolsModule.onEnable(this);
        enabledModules.add("MythicTools");


        billfordModule = new BillfordModule();
        billfordModule.onEnable(this);
        enabledModules.add("Billford");

        statsModule = new StatsModule();
        statsModule.onEnable(this);
        enabledModules.add("Stats");

        leaderboardModule = new LeaderboardModule();
        leaderboardModule.onEnable(this);
        enabledModules.add("Leaderboard");

        cratesModule = new CratesModule();
        cratesModule.onEnable(this);
        enabledModules.add("Crates");

        clearInventoryModule = new ClearInventoryModule();
        clearInventoryModule.onEnable(this);
        enabledModules.add("ClearInventory");

        nightVisionModule = new NightVisionModule();
        nightVisionModule.onEnable(this);
        enabledModules.add("NightVision");

        globalModule = new GlobalModule();
        globalModule.onEnable(this);
        enabledModules.add("Global");

        chatModule = new ChatModule();
        chatModule.onEnable(this);
        enabledModules.add("Chat");

        teleportModule = new TeleportModule();
        teleportModule.onEnable(this);
        enabledModules.add("Teleport");

        scoreboardModule = new ScoreboardModule();
        scoreboardModule.onEnable(this);
        enabledModules.add("Scoreboard");

        settingsModule = new SettingsModule();
        settingsModule.onEnable(this);
        enabledModules.add("Settings");

        spawnModule = new SpawnModule();
        spawnModule.onEnable(this);
        enabledModules.add("Spawn");
        ;

        // The RTP zone (WorldGuard region -> countdown -> random overworld teleport) lives on the
        // spawn/hub server, so it only enables there. It reuses RTP's shared pools for destinations.
        if (serverRole == ServerRole.SPAWN) {
            rtpZoneModule = new RtpZoneModule();
            rtpZoneModule.onEnable(this);
            enabledModules.add("RtpZone");
        }

        if (serverRole == ServerRole.SURVIVAL || serverRole == ServerRole.SPAWN) {
            timeSyncModule = new TimeSyncModule();
            timeSyncModule.onEnable(this);
            enabledModules.add("TimeSync");
        }

        if (serverRole == ServerRole.SURVIVAL) {

            borderModule = new BorderModule();
            borderModule.onEnable(this);
            enabledModules.add("Borders");

            pearlStasisModule = new PearlStasisModule();
            pearlStasisModule.onEnable(this);
            enabledModules.add("PearlStasis");
        }

        // Survival servers hand their End portals to the dedicated End backend; the End
        // backend sends players leaving through the exit portal back to an overworld.
        if (serverRole == ServerRole.SURVIVAL || serverRole == ServerRole.END) {
            endPortalModule = new EndPortalModule();
            endPortalModule.onEnable(this);
            enabledModules.add("EndPortal");
        }

        if (getCommand("ravencore") != null) {
            RavenCoreCommand command = new RavenCoreCommand(this);
            getCommand("ravencore").setExecutor(command);
            getCommand("ravencore").setTabCompleter(command);
        }
        DocsGenerator.write(this);
        printReady(System.currentTimeMillis() - startedAt);
    }

    public List<String> reloadableModuleNames() {
        return new ArrayList<>(reloadableModules().keySet());
    }

    public boolean reloadModuleSafely(String name) {
        KModule<?> module = reloadableModules().get(name.toLowerCase());
        if (module == null) {
            return false;
        }

        SchedulerUtil.run(() -> {
            try {
                module.reload();
                getLogger().info("[RavenCore] Reloaded module: " + name);
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.SEVERE, "[RavenCore] Failed to reload module: " + name, e);
            }
        });
        return true;
    }

    public void reloadAllModulesSafely() {
        List<String> names = reloadableModuleNames();
        reloadAllModulesSafely(names, 0);
    }

    private void reloadAllModulesSafely(List<String> names, int index) {
        if (index >= names.size()) {
            return;
        }

        reloadModuleSafely(names.get(index));
        SchedulerUtil.runLater(() -> reloadAllModulesSafely(names, index + 1), 5L);
    }

    private Map<String, KModule<?>> reloadableModules() {
        Map<String, KModule<?>> modules = new LinkedHashMap<>();
        putModule(modules, "core", null);
        putModule(modules, "economy", economyModule);
        putModule(modules, "shop", shopModule);
        putModule(modules, "mythictools", mythicToolsModule);
        putModule(modules, "billford", billfordModule);
        putModule(modules, "stats", statsModule);
        putModule(modules, "leaderboard", leaderboardModule);
        putModule(modules, "crates", cratesModule);
        putModule(modules, "clearinventory", clearInventoryModule);
        putModule(modules, "nightvision", nightVisionModule);
        putModule(modules, "global", globalModule);
        putModule(modules, "chat", chatModule);
        putModule(modules, "scoreboard", scoreboardModule);
        putModule(modules, "serverinfo", serverInfoModule);
        putModule(modules, "settings", settingsModule);
        putModule(modules, "teleport", teleportModule);
        putModule(modules, "spawn", spawnModule);
        putModule(modules, "rtpzone", rtpZoneModule);
        putModule(modules, "lastlocation", lastLocationModule);
        putModule(modules, "locationpool", locationPoolModule);
        putModule(modules, "rtp", rtpModule);
        putModule(modules, "respawn", respawnModule);
        putModule(modules, "joinfix", joinFixModule);
        putModule(modules, "home", homeModule);
        putModule(modules, "team", teamModule);
        putModule(modules, "combat", combatModule);
        putModule(modules, "timesync", timeSyncModule);
        putModule(modules, "borders", borderModule);
        putModule(modules, "pearlstasis", pearlStasisModule);
        putModule(modules, "endportal", endPortalModule);
        return modules;
    }

    private void putModule(Map<String, KModule<?>> modules, String name, KModule<?> module) {
        if (module != null) {
            modules.put(name, module);
            return;
        }
        if (name.equals("core")) {
            modules.put(name, new CoreConfigModule());
        }
    }

    private final class CoreConfigModule extends KModule<RavenCore> {
        private CoreConfigModule() {
            super("core");
        }

        @Override
        public void onModuleEnable() {}

        @Override
        public void onModuleDisable() {}

        @Override
        public void reload() {
            RavenCore.this.reloadConfig();
        }
    }
    /** Console banner shown at the very start of enable, before modules load. */
    private void printBanner() {
        var console = Bukkit.getConsoleSender();
        console.sendMessage(ChatFormatter.color("&#7A7A7A&m                                        "));
        console.sendMessage(ChatFormatter.color("&#FF2B2B&lRaven&#D90429&lCore &#7A7A7Av" + getDescription().getVersion()));
        console.sendMessage(ChatFormatter.color("&#7A7A7AServer: &#FFFFFF" + serverDisplayName
                + " &#7A7A7A(" + serverId + ") &#7A7A7A| Role: &#FFFFFF" + serverRole));
        console.sendMessage(ChatFormatter.color("&#7A7A7AStarting up..."));
        console.sendMessage(ChatFormatter.color("&#7A7A7A&m                                        "));
    }

    /** Console summary shown once every module has finished loading. */
    private void printReady(long elapsedMs) {
        var console = Bukkit.getConsoleSender();
        console.sendMessage(ChatFormatter.color("&#7A7A7A&m                                        "));
        console.sendMessage(ChatFormatter.color("&#33FF66&lRavenCore enabled &#7A7A7Ain &#FFFFFF" + elapsedMs + "ms"));
        console.sendMessage(ChatFormatter.color("&#7A7A7AModules (&#FFFFFF" + enabledModules.size() + "&#7A7A7A): &#FFFFFF"
                + String.join("&#7A7A7A, &#FFFFFF", enabledModules)));
        console.sendMessage(ChatFormatter.color("&#7A7A7A&m                                        "));
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

    private void initializeSkinCache() {
        SkinCacheAPI.init(this, mySqlManager != null && mySqlManager.isConnected() ? mySqlManager : null)
                .thenAccept(api -> {
                    getLogger().info("[SkinCache] Initialized "
                            + (mySqlManager != null && mySqlManager.isConnected() ? "with MySQL storage." : "with SQLite fallback."));
                    Bukkit.getOnlinePlayers().forEach(api::cacheOnlinePlayer);
                })
                .exceptionally(error -> {
                    getLogger().warning("[SkinCache] Failed to initialize: " + error.getMessage());
                    return null;
                });

        Bukkit.getPluginManager().registerEvent(
                PlayerJoinEvent.class, new Listener() {}, EventPriority.MONITOR,
                (listener, event) -> {
                    if (SkinCacheAPI.isInitialized()) {
                        SkinCacheAPI.getInstance().cacheOnlinePlayer(((PlayerJoinEvent) event).getPlayer());
                    }
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
     * borders. Validate the already-loaded world and retain the WorldLoadEvent fallback
     * for worlds loaded later by server-management plugins.
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
        if (unloadWorldAsync(world, logMessage)) {
            return;
        }

        SchedulerUtil.run(() -> {
            if (isTickingWorlds()) {
                getLogger().warning("[Role] Cannot unload world '" + world.getName()
                        + "' while worlds are ticking on this runtime. Disable this world in server config.");
                return;
            }

            try {
                if (Bukkit.unloadWorld(world, false)) {
                    getLogger().info("[Role] " + logMessage);
                } else {
                    getLogger().warning("[Role] Failed to unload world '" + world.getName() + "' for role " + serverRole);
                }
            } catch (UnsupportedOperationException | IllegalStateException e) {
                getLogger().warning("[Role] Runtime does not support synchronous world unload for '"
                        + world.getName() + "': " + e.getMessage());
            }
        });
    }

    private boolean unloadWorldAsync(World world, String logMessage) {
        try {
            var method = Bukkit.class.getMethod("unloadWorldAsync", World.class, boolean.class, Consumer.class);
            Consumer<Object> callback = result -> getLogger().info("[Role] " + logMessage + " (" + result + ")");
            method.invoke(null, world, false, callback);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().warning("[Role] Async world unload failed for '" + world.getName() + "': " + e.getMessage());
            return true;
        }
    }

    private boolean isTickingWorlds() {
        try {
            Object value = Bukkit.getServer().getClass().getMethod("isTickingWorlds").invoke(Bukkit.getServer());
            return value instanceof Boolean ticking && ticking;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (scoreboardModule != null) scoreboardModule.onDisable(this);
        if (serverInfoModule != null) serverInfoModule.onDisable(this);
        if (combatModule != null) combatModule.onDisable(this);
        if (teamModule != null) teamModule.onDisable(this);
        if (homeModule != null) homeModule.onDisable(this);
        if (respawnModule != null) respawnModule.onDisable(this);
        if (joinFixModule != null) joinFixModule.onDisable(this);
        if (rtpModule != null) rtpModule.onDisable(this);
        if (spawnModule != null) spawnModule.onDisable(this);
        if (rtpZoneModule != null) rtpZoneModule.onDisable(this);
        if (settingsModule != null) settingsModule.onDisable(this);
        if (teleportModule != null) teleportModule.onDisable(this);
        if (borderModule != null) borderModule.onDisable(this);
        if (pearlStasisModule != null) pearlStasisModule.onDisable(this);
        if (endPortalModule != null) endPortalModule.onDisable(this);
        if (timeSyncModule != null) {
            timeSyncModule.onDisable(this);
        }
        if (clearInventoryModule != null) {
            clearInventoryModule.onDisable(this);
        }
        if (nightVisionModule != null) {
            nightVisionModule.onDisable(this);
        }
        if (globalModule != null) {
            globalModule.onDisable(this);
        }
        if (chatModule != null) {
            chatModule.onDisable(this);
        }
        if (shopModule != null) {
            shopModule.onDisable(this);
        }
        if (mythicToolsModule != null) {
            mythicToolsModule.onDisable(this);
        }
        if (billfordModule != null) {
            billfordModule.onDisable(this);
        }
        if (statsModule != null) {
            statsModule.onDisable(this);
        }
        if (leaderboardModule != null) {
            leaderboardModule.onDisable(this);
        }
        if (cratesModule != null) {
            cratesModule.onDisable(this);
        }



        if (economyModule != null) {
            economyModule.onDisable(this);
        }
        if (lastLocationModule != null) {
            lastLocationModule.onDisable(this);
        }
        if (locationPoolModule != null) {
            locationPoolModule.onDisable(this);
        }
        if (redisMessenger != null) {
            redisMessenger.close();
        }
        if (SkinCacheAPI.isInitialized()) {
            SkinCacheAPI.shutdown().join();
        }
        if (mySqlManager != null) {
            mySqlManager.disconnect();
        }
    }

}
