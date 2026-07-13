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
import java.util.Optional;

public class RtpModule extends KModule<RavenCore> {

    private RtpPoolConfig poolConfig;
    private RtpPoolStore poolStore;
    private SchedulerUtil.Task refillTask;

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

        DimensionMenu dimensionMenu = new DimensionMenu(api, service, destinations);
        RegionMenu regionMenu = new RegionMenu(api, service, destinations, dimensionMenu);
        dimensionMenu.setRegionMenu(regionMenu);

        registerCommand(new RtpCommand(dimensionMenu), "rtp");

        onEvent(PlayerJoinEvent.class, event ->
                RtpTransferHelper.consumeOnJoin(api, api.getRedisMessenger().getConnection().getPool(), event.getPlayer()));

        Optional<RegionDestination> localDestination = destinations.findByServer(api.getServerId());
        if (localDestination.isEmpty()) {
            getLogger().warning("[Rtp] This server's id (" + api.getServerId()
                    + ") isn't listed in rtp/config.yml — its pool will never be filled.");
        } else {
            startPoolMaintenance(localDestination.get());
        }

        getLogger().info("[Rtp] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (refillTask != null) refillTask.cancel();
        getLogger().info("[Rtp] Disabled.");
    }

    // -------------------------------------------------------------------------

    /**
     * Plugins load with {@code load: STARTUP}, so this server's gameplay world isn't
     * guaranteed loaded yet — start the refill timer immediately if it already is,
     * otherwise wait for it via WorldLoadEvent (mirrors RavenCore.enforceWorldSeed).
     */
    private void startPoolMaintenance(RegionDestination destination) {
        World world = resolveLocalWorld();
        if (world != null) {
            beginRefillTimer(world, destination);
            return;
        }

        World.Environment required = requiredEnvironment();
        onEvent(WorldLoadEvent.class, event -> {
            if (refillTask == null && event.getWorld().getEnvironment() == required) {
                beginRefillTimer(event.getWorld(), destination);
            }
        });
    }

    private void beginRefillTimer(World world, RegionDestination destination) {
        long intervalTicks = Math.max(1, poolConfig.refillIntervalSeconds()) * 20L;
        refillTask = SchedulerUtil.runAsyncTimer(() -> ensureFilled(world, destination), 0L, intervalTicks);
    }

    /** Tops the pool back up to target-size whenever it drops to/below the refill threshold. */
    private void ensureFilled(World world, RegionDestination destination) {
        long current = poolStore.size(api.getServerId());
        if (current > poolConfig.refillThreshold()) return;

        long deficit = poolConfig.targetSize() - current;
        for (long i = 0; i < deficit; i++) {
            SafeLocationFinder.find(world, destination, poolConfig).thenAccept(found -> {
                if (found.isEmpty()) return;
                Bukkit.getAsyncScheduler().runNow(api, task -> poolStore.push(api.getServerId(), found.get()));
            });
        }
    }

    private World resolveLocalWorld() {
        World.Environment env = requiredEnvironment();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == env) return world;
        }
        return null;
    }

    private World.Environment requiredEnvironment() {
        return switch (api.getServerRole()) {
            case NETHER -> World.Environment.NETHER;
            case END -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
