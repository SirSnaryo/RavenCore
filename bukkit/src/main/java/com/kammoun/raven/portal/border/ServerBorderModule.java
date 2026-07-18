package com.kammoun.raven.portal.border;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.portal.tracker.PendingTransferTracker;
import com.kammoun.raven.portal.config.PortalMessages;
import com.kammoun.raven.portal.model.OverworldRegion;
import com.kammoun.raven.portal.model.ServerBorder;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class ServerBorderModule extends KModule<RavenCore> {

    /** How long a transfer is given to complete before border enforcement resumes for that player. */
    private static final long TRANSFER_TIMEOUT_MILLIS = 5000L;

    private JedisPool jedisPool;
    private List<ServerBorder> normalBorders;
    private List<ServerBorder> netherBorders;
    private String thisServer;

    private PendingTransferTracker pendingTransfers;
    private BorderEnforcer enforcer;
    private BorderParticleRenderer particleRenderer;

    private ScheduledTask tickTask;

    public ServerBorderModule() {
        super("portal"); // shares the portal module's data folder and config
    }

    @Override
    public void onModuleEnable() {
        jedisPool  = api.getRedisMessenger().getConnection().getPool();
        thisServer = api.getServerId();

        double detectionDistance = getConfig().getDouble("border.detection-distance", 30.0);
        double particleSpacing   = getConfig().getDouble("border.particle-spacing",   1.5);
        int wallHeight           = getConfig().getInt   ("border.wall-height",        20);
        Particle particle        = parseParticle(getConfig().getString("border.particle", "DUST_PLUME"));
        PortalMessages messages  = new PortalMessages(getConfig());

        List<OverworldRegion> regions = OverworldRegion.loadAll(getConfig());
        normalBorders = ServerBorder.deriveAll(regions).stream()
                .filter(border -> border.involves(thisServer))
                .toList();

        // Each region's local Nether mirrors its Overworld footprint at Minecraft's normal 1:8
        // ratio, so the same shared-edge derivation gives us the matching Nether borders too.
        List<OverworldRegion> netherRegions = OverworldRegion.scaled(regions, 8.0);
        netherBorders = ServerBorder.deriveAll(netherRegions).stream()
                .filter(border -> border.involves(thisServer))
                .toList();

        if (normalBorders.isEmpty() && netherBorders.isEmpty()) {
            getLogger().info("[Border] No borders derived — module idle.");
            return;
        }

        pendingTransfers = new PendingTransferTracker(TRANSFER_TIMEOUT_MILLIS);
        enforcer         = new BorderEnforcer(api, jedisPool, normalBorders, netherBorders, thisServer, pendingTransfers, messages);
        particleRenderer = new BorderParticleRenderer(detectionDistance, particleSpacing, wallHeight, particle);

        onEvent(PlayerQuitEvent.class, e -> pendingTransfers.clear(e.getPlayer().getUniqueId()));
        // A player who just joined via a border transfer is still sitting at this world's
        // default spawn until DimensionPortalModule's async redis lookup lands them at the
        // real destination. If that spot happens to read as "beyond our side" (e.g. spawn
        // sits right on x=0 and the border is also at x=0), border checks would fire before
        // the real placement ever applies and bounce them straight back — a ping-pong loop.
        // Hold enforcement off for a moment so the real teleport always wins the race.
        onEvent(PlayerJoinEvent.class, e -> pendingTransfers.mark(e.getPlayer().getUniqueId()));
        onEvent(PlayerMoveEvent.class, enforcer::onMove);
        onEvent(PlayerTeleportEvent.class, enforcer::onTeleport);
        onEvent(VehicleMoveEvent.class, enforcer::onVehicleMove);
        onEvent(BlockBreakEvent.class, enforcer::onBlockBreak);
        onEvent(BlockPlaceEvent.class, enforcer::onBlockPlace);
        onEvent(PlayerBucketEmptyEvent.class, enforcer::onBucketEmpty);
        onEvent(PlayerBucketFillEvent.class, enforcer::onBucketFill);
        onEvent(PlayerInteractEvent.class, enforcer::onInteract);

        // Run every 10 ticks (0.5 s) on the global region scheduler (Folia-safe)
        tickTask = api.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(api, task -> tick(), 10L, 10L);

        getLogger().info("[Border] Enabled with " + normalBorders.size() + " overworld border(s), "
                + netherBorders.size() + " nether border(s).");
    }

    @Override
    public void onModuleDisable() {
        if (tickTask != null) tickTask.cancel();
        if (pendingTransfers != null) pendingTransfers.clearAll();
    }

    // -------------------------------------------------------------------------

    private void tick() {
        for (Player player : api.getServer().getOnlinePlayers()) {
            if (pendingTransfers.isPending(player.getUniqueId())) continue;

            Location loc = player.getLocation();
            World world  = loc.getWorld();
            if (world == null) continue;

            List<ServerBorder> borders = enforcer.bordersFor(world.getEnvironment());
            if (borders.isEmpty()) continue;

            double px = loc.getX(), pz = loc.getZ();

            for (ServerBorder border : borders) {
                particleRenderer.renderIfNear(player, border, loc);

                // Safety net: catches anyone found beyond our side regardless of how they
                // got there — riding a boat/minecart (fires VehicleMoveEvent, which onMove
                // never sees), joining already past the line, a border config change, etc.
                // Unlike the move/teleport clamp this doesn't correct their position, it
                // just makes sure the transfer always gets kicked off.
                if (border.overshoot(thisServer, px, pz) > 0) {
                    enforcer.triggerTransfer(player, enforcer.arrivalLocation(loc, border), border.otherServer(thisServer));
                }
            }
        }
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("[Border] Unknown particle '" + name + "', falling back to DUST_PLUME.");
            return Particle.DUST_PLUME;
        }
    }
}
