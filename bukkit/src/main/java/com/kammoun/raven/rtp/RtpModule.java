package com.kammoun.raven.rtp;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.rtp.command.RtpCommand;
import com.kammoun.raven.rtp.config.RtpDestinations;
import com.kammoun.raven.rtp.config.RtpDestinations.RegionDestination;
import com.kammoun.raven.rtp.config.RtpMessages;
import com.kammoun.raven.rtp.config.RtpPoolConfig;
import com.kammoun.raven.rtp.menu.DimensionMenu;
import com.kammoun.raven.rtp.menu.RegionMenu;
import com.kammoun.raven.portal.model.OverworldRegion;
import com.kammoun.raven.rtp.pool.RtpPoolStore;
import com.kammoun.raven.rtp.pool.SafeLocationFinder;
import com.kammoun.raven.rtp.service.RtpService;
import com.kammoun.raven.rtp.transfer.RtpTransferHelper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class RtpModule extends KModule<RavenCore> {

    private RtpPoolConfig poolConfig;
    private RtpPoolStore poolStore;
    private final List<SchedulerUtil.Task> refillTasks = new ArrayList<>();

    /** Region boxes from the portal module, keyed by server — the source of truth RTP samples within. */
    private Map<String, OverworldRegion> overworldRegionsByServer = new HashMap<>();
    private Map<String, OverworldRegion> netherRegionsByServer = new HashMap<>();

    public RtpModule() {
        super("rtp");
    }

    @Override
    public void onModuleEnable() {
        RtpDestinations destinations = RtpDestinations.load(getConfig());
        poolConfig = RtpPoolConfig.load(getConfig());
        loadPortalRegions();
        int cooldownSeconds = getConfig().getInt("cooldown-seconds", 5);

        poolStore = new RtpPoolStore(api, api.getRedisMessenger().getConnection().getPool());
        RtpMessages messages = new RtpMessages(loadMessages());
        RtpService service = new RtpService(api, poolStore, messages, getBungeeHelper(), cooldownSeconds);

        RegionMenu overworldRegionMenu = new RegionMenu(api, service, destinations.overworldWorlds(), "rtp/region-menu.yml");
        // The Nether is server-blind: clicking it teleports to a random Nether server rather than
        // opening a region picker, so it needs a generic label (not a per-region NA/EU name).
        String netherLabel = getConfig().getString("nether-display-name", "the Nether");
        DimensionMenu dimensionMenu = new DimensionMenu(api, service, destinations, overworldRegionMenu, netherLabel);
        overworldRegionMenu.setBackTarget(dimensionMenu);

        registerCommand(new RtpCommand(dimensionMenu), "rtp");

        onEvent(PlayerJoinEvent.class, event ->
                RtpTransferHelper.consumeOnJoin(api, api.getRedisMessenger().getConnection().getPool(), event.getPlayer()));

        List<RegionDestination> localDestinations = destinations.findAllByServer(api.getServerId());
        if (localDestinations.isEmpty()) {
            getLogger().warning("[Rtp] This server's id (" + api.getServerId()
                    + ") isn't listed in rtp/config.yml — its pool(s) will never be filled.");
        } else {
            for (RegionDestination destination : localDestinations) {
                startPoolMaintenance(destination);
            }
        }

        getLogger().info("[Rtp] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        refillTasks.forEach(SchedulerUtil.Task::cancel);
        getLogger().info("[Rtp] Disabled.");
    }

    // -------------------------------------------------------------------------

    /**
     * Plugins load with {@code load: STARTUP}, so this server's gameplay worlds aren't
     * guaranteed loaded yet — start the refill timer immediately if this destination's world
     * already is, otherwise wait for it via WorldLoadEvent (mirrors RavenCore.enforceWorldSeed).
     * A survival server has two of these running side by side: one for its Overworld region,
     * one for that region's local Nether.
     */
    private void startPoolMaintenance(RegionDestination destination) {
        OverworldRegion region = regionFor(destination);

        // Picks are always bounded by this world's world border (see SafeLocationFinder), so an
        // unmatched portal region no longer risks dropping players across a server border — it just
        // means the world border alone defines the search box. Warn so the config mismatch still
        // gets fixed and the precise region clamp is restored, but keep the pool running.
        if (region == null && destination.environment() != World.Environment.THE_END) {
            getLogger().warning("[Rtp] No portal overworld-region matches server '" + destination.server()
                    + "' for destination '" + destination.id() + "' — bounding RTP picks by this world's"
                    + " world border only. Ensure the world border covers just this server's territory, or"
                    + " reconcile the 'server' ids in rtp/config.yml and portal/config.yml.");
        }

        World world = resolveLocalWorld(destination.environment());
        if (world != null) {
            beginRefillTimer(world, destination, region);
            return;
        }

        AtomicBoolean started = new AtomicBoolean(false);
        onEvent(WorldLoadEvent.class, event -> {
            if (started.get() || event.getWorld().getEnvironment() != destination.environment()) return;
            started.set(true);
            beginRefillTimer(event.getWorld(), destination, region);
        });
    }

    private void beginRefillTimer(World world, RegionDestination destination, OverworldRegion region) {
        long intervalTicks = Math.max(1, poolConfig.refillIntervalSeconds()) * 20L;
        refillTasks.add(SchedulerUtil.runAsyncTimer(() -> ensureFilled(world, destination, region), 0L, intervalTicks));
    }

    /** Tops the pool back up to target-size whenever it drops to/below the refill threshold. */
    private void ensureFilled(World world, RegionDestination destination, OverworldRegion region) {
        long current = poolStore.size(destination.id());
        if (current > poolConfig.refillThreshold()) return;

        long deficit = poolConfig.targetSize() - current;
        for (long i = 0; i < deficit; i++) {
            SafeLocationFinder.find(world, destination, poolConfig, region).thenAccept(found -> {
                if (found.isEmpty()) return;
                Bukkit.getAsyncScheduler().runNow(api, task -> poolStore.push(destination.id(), found.get()));
            });
        }
    }

    /**
     * The portal region box this destination must stay within, so RTP never hands out a
     * spot in a neighbouring server's half of the shared overworld/nether coordinate space,
     * or {@code null} if none matches. For overworld/nether destinations a {@code null} here
     * is a fatal misconfig — {@link #startPoolMaintenance} disables the pool rather than let
     * {@link SafeLocationFinder} radius-sample across the border. Only the End legitimately has
     * no region and samples by radius.
     */
    private OverworldRegion regionFor(RegionDestination destination) {
        return switch (destination.environment()) {
            case NORMAL -> overworldRegionsByServer.get(destination.server());
            case NETHER -> netherRegionsByServer.get(destination.server());
            default -> null;
        };
    }

    /**
     * Reads the portal module's overworld-regions (shared coordinate boxes) as RTP's source of truth.
     * Relies on RavenCore enabling DimensionPortalModule (which writes portal/config.yml) before RTP —
     * true on every gameplay server. On a lobby the portal module is skipped, but a lobby backs no RTP
     * pools of its own, so an absent file there just leaves these maps empty and is harmless.
     */
    private void loadPortalRegions() {
        File portalConfig = new File(getDataFolder().getParentFile(), "portal/config.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(portalConfig);
        List<OverworldRegion> regions = OverworldRegion.loadAll(cfg);

        overworldRegionsByServer = new HashMap<>();
        netherRegionsByServer = new HashMap<>();
        for (OverworldRegion region : regions) {
            overworldRegionsByServer.put(region.getServer(), region);
        }
        // Each region's local Nether is its Overworld footprint at Minecraft's 1:8 ratio — matches
        // ServerBorderModule, so RTP and border enforcement agree on where the Nether split sits.
        for (OverworldRegion region : OverworldRegion.scaled(regions, 8.0)) {
            netherRegionsByServer.put(region.getServer(), region);
        }
    }

    private World resolveLocalWorld(World.Environment environment) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) return world;
        }
        return null;
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
