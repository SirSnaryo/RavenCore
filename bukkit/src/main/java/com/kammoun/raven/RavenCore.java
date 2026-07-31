package com.kammoun.raven;

import com.kammoun.raven.billford.BillfordModule;
import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.chat.ChatModule;
import com.kammoun.raven.combat.CombatModule;
import com.kammoun.raven.clearinventory.ClearInventoryModule;
import com.kammoun.raven.command.RavenCoreCommand;
import com.kammoun.raven.economy.EconomyModule;
import com.kammoun.raven.globalcontrol.GlobalModule;
import com.kammoun.raven.home.HomeModule;
import com.kammoun.raven.lastlocation.LastLocationModule;
import com.kammoun.raven.leaderboard.LeaderboardModule;
import com.kammoun.raven.locationPool.LocationPoolModule;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.library.bootstrap.PluginClassPreloader;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.rtpzone.RtpZoneModule;
import com.kammoun.raven.rtp.RtpModule;
import com.kammoun.raven.respawn.RespawnModule;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private BillfordModule billfordModule;
    private StatsModule statsModule;
    private LeaderboardModule leaderboardModule;
    private BorderModule borderModule;

    @Override
    public void onEnable() {
        long startedAt = System.currentTimeMillis();
        instance = this;
        saveDefaultConfig();
        migrateCoreConfigFile();
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

        clearInventoryModule = new ClearInventoryModule();
        clearInventoryModule.onEnable(this);
        enabledModules.add("ClearInventory");

        globalModule = new GlobalModule();
        globalModule.onEnable(this);
        enabledModules.add("Global");

        chatModule = new ChatModule();
        chatModule.onEnable(this);
        enabledModules.add("Chat");

        teleportModule = new TeleportModule();
        teleportModule.onEnable(this);
        enabledModules.add("Teleport");

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

        if (serverRole == ServerRole.SURVIVAL) {
            timeSyncModule = new TimeSyncModule();
            timeSyncModule.onEnable(this);
            enabledModules.add("TimeSync");

            borderModule = new BorderModule();
            borderModule.onEnable(this);
            enabledModules.add("Borders");
        }

        if (getCommand("ravencore") != null) {
            RavenCoreCommand command = new RavenCoreCommand(this);
            getCommand("ravencore").setExecutor(command);
            getCommand("ravencore").setTabCompleter(command);
        }
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
        putModule(modules, "clearinventory", clearInventoryModule);
        putModule(modules, "global", globalModule);
        putModule(modules, "chat", chatModule);
        putModule(modules, "settings", settingsModule);
        putModule(modules, "teleport", teleportModule);
        putModule(modules, "spawn", spawnModule);
        putModule(modules, "rtpzone", rtpZoneModule);
        putModule(modules, "lastlocation", lastLocationModule);
        putModule(modules, "locationpool", locationPoolModule);
        putModule(modules, "rtp", rtpModule);
        putModule(modules, "respawn", respawnModule);
        putModule(modules, "home", homeModule);
        putModule(modules, "team", teamModule);
        putModule(modules, "combat", combatModule);
        putModule(modules, "timesync", timeSyncModule);
        putModule(modules, "borders", borderModule);
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
            RavenCore.this.migrateCoreConfigFile();
            RavenCore.this.reloadConfig();
        }
    }

    /**
     * Bukkit only copies config.yml the first time. This adds new core keys to
     * existing installs with a tiny line-based migration so comments/order stay intact.
     */
    private void migrateCoreConfigFile() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.isFile()) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines);
            boolean changed = false;

            if (!hasNetworkKey(updated, "server-display-name")) {
                List<String> displayNameBlock = List.of(
                        "  # Friendly server name shown in GUI lore/messages.",
                        "  # Example: \"NA East\", \"SMP World NA\", \"Spawn\".",
                        "  # Leave empty to reuse network.server-id.",
                        "  server-display-name: ''"
                );
                int networkLine = findTopLevelSection(updated, "network");
                if (networkLine == -1) {
                    if (!updated.isEmpty() && !updated.get(updated.size() - 1).isBlank()) updated.add("");
                    updated.add("network:");
                    updated.add("  server-id: ''");
                    updated.addAll(displayNameBlock);
                } else {
                    int serverIdLine = findSectionKey(updated, networkLine, "server-id");
                    updated.addAll(serverIdLine == -1 ? networkLine + 1 : serverIdLine + 1, displayNameBlock);
                }
                changed = true;
            }

            YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
            int cellSize = existing.getInt("grid.cell-size", 5000);
            int ratio = Math.max(1, existing.getInt("grid.nether-ratio", 8));
            int cellX = existing.getInt("grid.cell-x", 0);
            int cellZ = existing.getInt("grid.cell-z", 0);
            int margin = Math.max(0, existing.getInt("grid.border-safety-margin", 300));
            long overworldMinX = (long) cellX * cellSize + margin;
            long overworldMinZ = (long) cellZ * cellSize + margin;
            long overworldMaxX = (long) (cellX + 1) * cellSize - margin;
            long overworldMaxZ = (long) (cellZ + 1) * cellSize - margin;
            int netherSize = cellSize / ratio;
            int netherMargin = (int) Math.ceil(margin / (double) ratio);
            long netherMinX = (long) cellX * netherSize + netherMargin;
            long netherMinZ = (long) cellZ * netherSize + netherMargin;
            long netherMaxX = (long) (cellX + 1) * netherSize - netherMargin;
            long netherMaxZ = (long) (cellZ + 1) * netherSize - netherMargin;

            int locationLine = findTopLevelSection(updated, "location-library");
            if (locationLine == -1) {
                if (!updated.isEmpty() && !updated.get(updated.size() - 1).isBlank()) updated.add("");
                updated.add("location-library:");
                updated.add("  refill-interval-seconds: 45");
                updated.add("  nether-pool-enabled: true");
                locationLine = updated.size() - 3;
                changed = true;
            }

            List<String> additions = new ArrayList<>();
            if (findSectionKey(updated, locationLine, "bounds") == -1) {
                additions.addAll(List.of(
                        "  bounds:",
                        "    overworld:",
                        "      min: { x: " + overworldMinX + ", y: -64, z: " + overworldMinZ + " }",
                        "      max: { x: " + overworldMaxX + ", y: 320, z: " + overworldMaxZ + " }",
                        "    nether:",
                        "      min: { x: " + netherMinX + ", y: 5, z: " + netherMinZ + " }",
                        "      max: { x: " + netherMaxX + ", y: 120, z: " + netherMaxZ + " }",
                        "    end:",
                        "      min: { x: -5000, y: 1, z: -5000 }",
                        "      max: { x: 5000, y: 255, z: 5000 }"
                ));
            }
            if (findSectionKey(updated, locationLine, "blacklisted-blocks") == -1) {
                additions.addAll(List.of(
                        "  blacklisted-blocks:",
                        "    - WATER", "    - LAVA", "    - CACTUS", "    - BEDROCK",
                        "    - KELP", "    - MAGMA_BLOCK", "    - FIRE", "    - SOUL_FIRE",
                        "    - CAMPFIRE", "    - SOUL_CAMPFIRE", "    - POWDER_SNOW",
                        "    - SWEET_BERRY_BUSH", "    - OAK_LEAVES", "    - SPRUCE_LEAVES",
                        "    - BIRCH_LEAVES", "    - JUNGLE_LEAVES", "    - ACACIA_LEAVES",
                        "    - DARK_OAK_LEAVES", "    - MANGROVE_LEAVES", "    - CHERRY_LEAVES",
                        "    - PALE_OAK_LEAVES"
                ));
            }
            if (findSectionKey(updated, locationLine, "blacklisted-biomes") == -1) {
                additions.addAll(List.of("  blacklisted-biomes:", "    - BEACH"));
            }
            if (!additions.isEmpty()) {
                updated.addAll(findSectionEnd(updated, locationLine), additions);
                changed = true;
            }

            int boundsLine = findSectionKey(updated, locationLine, "bounds");
            if (boundsLine != -1 && !containsExactLine(updated, "    end:")) {
                updated.addAll(findNestedSectionEnd(updated, boundsLine, 4), List.of(
                        "    end:",
                        "      min: { x: -5000, y: 1, z: -5000 }",
                        "      max: { x: 5000, y: 255, z: 5000 }"
                ));
                changed = true;
            }

            if (changed) {
                Files.write(configFile.toPath(), updated, StandardCharsets.UTF_8);
                getLogger().info("[Config] Added missing RavenCore configuration defaults.");
            }
        } catch (IOException e) {
            getLogger().warning("[Config] Failed to migrate config.yml: " + e.getMessage());
        }
    }

    private boolean hasNetworkKey(List<String> lines, String key) {
        return findSectionKey(lines, findTopLevelSection(lines, "network"), key) != -1;
    }

    private int findTopLevelSection(List<String> lines, String section) {
        String expected = section + ":";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank() || line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            if (line.trim().equals(expected)) {
                return i;
            }
        }
        return -1;
    }

    private int findSectionKey(List<String> lines, int sectionLine, String key) {
        if (sectionLine < 0) {
            return -1;
        }

        String expected = key + ":";
        for (int i = sectionLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                return -1;
            }
            if (trimmed.equals(expected) || trimmed.startsWith(expected + " ")) {
                return i;
            }
        }
        return -1;
    }

    private int findSectionEnd(List<String> lines, int sectionLine) {
        for (int i = sectionLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")
                    && !line.trim().startsWith("#")) {
                return i;
            }
        }
        return lines.size();
    }

    private int findNestedSectionEnd(List<String> lines, int sectionLine, int childIndent) {
        for (int i = sectionLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            if (indent < childIndent) return i;
        }
        return lines.size();
    }

    private boolean containsExactLine(List<String> lines, String expected) {
        return lines.stream().anyMatch(expected::equals);
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
        if (combatModule != null) combatModule.onDisable(this);
        if (teamModule != null) teamModule.onDisable(this);
        if (homeModule != null) homeModule.onDisable(this);
        if (respawnModule != null) respawnModule.onDisable(this);
        if (rtpModule != null) rtpModule.onDisable(this);
        if (spawnModule != null) spawnModule.onDisable(this);
        if (rtpZoneModule != null) rtpZoneModule.onDisable(this);
        if (settingsModule != null) settingsModule.onDisable(this);
        if (teleportModule != null) teleportModule.onDisable(this);
        if (borderModule != null) borderModule.onDisable(this);
        if (timeSyncModule != null) {
            timeSyncModule.onDisable(this);
        }
        if (clearInventoryModule != null) {
            clearInventoryModule.onDisable(this);
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
