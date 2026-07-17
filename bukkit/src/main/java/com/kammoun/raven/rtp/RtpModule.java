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
import com.kammoun.raven.rtp.pool.RtpPoolStore;
import com.kammoun.raven.rtp.pool.SafeLocationFinder;
import com.kammoun.raven.rtp.transfer.RtpTransferHelper;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RtpModule extends KModule<RavenCore> {

    private RtpPoolConfig poolConfig;
    private RtpPoolStore poolStore;
    private final List<SchedulerUtil.Task> refillTasks = new ArrayList<>();

    public RtpModule() {
        super("rtp");
    }

    @Override
    public void onModuleEnable() {
        RtpDestinations destinations = RtpDestinations.load(getConfig());
        poolConfig = RtpPoolConfig.load(getConfig());
        int cooldownSeconds = getConfig().getInt("cooldown-seconds", 5);

        poolStore = new RtpPoolStore(api, api.getRedisMessenger().getConnection().getPool());
        RtpMessages messages = new RtpMessages(loadMessages());
        RtpService service = new RtpService(api, poolStore, messages, getBungeeHelper(), cooldownSeconds);

        RegionMenu overworldRegionMenu = new RegionMenu(api, service, destinations.overworldWorlds(), "rtp/region-menu.yml");
        RegionMenu netherRegionMenu = new RegionMenu(api, service, destinations.netherWorlds(), "rtp/nether-region-menu.yml");
        DimensionMenu dimensionMenu = new DimensionMenu(api, service, destinations, overworldRegionMenu, netherRegionMenu);
        overworldRegionMenu.setBackTarget(dimensionMenu);
        netherRegionMenu.setBackTarget(dimensionMenu);

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
        World world = resolveLocalWorld(destination.environment());
        if (world != null) {
            beginRefillTimer(world, destination);
            return;
        }

        AtomicBoolean started = new AtomicBoolean(false);
        onEvent(WorldLoadEvent.class, event -> {
            if (started.get() || event.getWorld().getEnvironment() != destination.environment()) return;
            started.set(true);
            beginRefillTimer(event.getWorld(), destination);
        });
    }

    private void beginRefillTimer(World world, RegionDestination destination) {
        long intervalTicks = Math.max(1, poolConfig.refillIntervalSeconds()) * 20L;
        refillTasks.add(SchedulerUtil.runAsyncTimer(() -> ensureFilled(world, destination), 0L, intervalTicks));
    }

    /** Tops the pool back up to target-size whenever it drops to/below the refill threshold. */
    private void ensureFilled(World world, RegionDestination destination) {
        long current = poolStore.size(destination.id());
        if (current > poolConfig.refillThreshold()) return;

        long deficit = poolConfig.targetSize() - current;
        for (long i = 0; i < deficit; i++) {
            SafeLocationFinder.find(world, destination, poolConfig).thenAccept(found -> {
                if (found.isEmpty()) return;
                Bukkit.getAsyncScheduler().runNow(api, task -> poolStore.push(destination.id(), found.get()));
            });
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
