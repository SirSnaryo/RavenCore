package com.kammoun.raven.portal;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.portal.helper.PortalTransferHelper;
import com.kammoun.raven.portal.model.OverworldRegion;
import com.kammoun.raven.portal.tracker.PendingTransferTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class DimensionPortalModule extends KModule<RavenCore> {

    /** How long a transfer is given to complete before portal detection resumes for that player. */
    private static final long TRANSFER_TIMEOUT_MILLIS = 5000L;

    private JedisPool jedisPool;
    private List<OverworldRegion> overworldRegions;
    private String netherServer;
    private String endServer;

    private PendingTransferTracker transferring;

    public DimensionPortalModule() {
        super("portal");
    }

    @Override
    public void onModuleEnable() {
        jedisPool        = api.getRedisMessenger().getConnection().getPool();
        netherServer      = getConfig().getString("servers.nether", "smp-nether");
        endServer         = getConfig().getString("servers.end",    "smp-end");
        overworldRegions  = OverworldRegion.loadAll(getConfig());
        transferring      = new PendingTransferTracker(TRANSFER_TIMEOUT_MILLIS);

        // PlayerPortalEvent only fires if the server would actually attempt the vanilla
        // dimension change. On these dedicated-per-dimension setups allow-nether is off (no
        // local Nether/End is wanted), so vanilla never tries and the event never fires.
        // Detect standing in the portal block directly instead, and keep the event listener
        // as a no-op safety net for any server where vanilla does still attempt it.
        onEvent(PlayerPortalEvent.class, this::onPortal);
        onEvent(PlayerMoveEvent.class, this::onMove);
        onEvent(PlayerJoinEvent.class, this::onJoin);
        onEvent(PlayerQuitEvent.class, e -> transferring.clear(e.getPlayer().getUniqueId()));
        onEvent(PlayerChangedWorldEvent.class, this::onWorldChange);

        getLogger().info("[Portal] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (transferring != null) transferring.clearAll();
    }

    // -------------------------------------------------------------------------

    private void onPortal(PlayerPortalEvent event) {
        TeleportCause cause = event.getCause();
        boolean isNether = cause == TeleportCause.NETHER_PORTAL;
        if (!isNether && cause != TeleportCause.END_PORTAL) return;

        if (attemptTransfer(event.getPlayer(), event.getFrom(), isNether)) {
            event.setCancelled(true);
        }
    }

    /** Primary detection: fires on every step, so it catches standing in a portal even when vanilla never would. */
    private void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Material block = to.getBlock().getType();
        if (block != Material.NETHER_PORTAL && block != Material.END_PORTAL) return;

        attemptTransfer(event.getPlayer(), to, block == Material.NETHER_PORTAL);
    }

    /**
     * Resolves the cross-server destination for a player standing in a nether/end portal and
     * kicks off the transfer, unless one is already in flight for this crossing. Returns true
     * if this is (or already was) a recognized portal crossing, so vanilla teleport can be
     * safely cancelled by callers that have an event to cancel.
     */
    private boolean attemptTransfer(Player player, Location loc, boolean isNetherPortal) {
        World.Environment env = loc.getWorld().getEnvironment();

        String targetServer;
        World.Environment targetEnvironment;
        double destX, destZ;

        if (isNetherPortal && env == World.Environment.NORMAL) {
            targetServer = netherServer;
            targetEnvironment = World.Environment.NETHER;
            destX = loc.getX() / 8.0;
            destZ = loc.getZ() / 8.0;
        } else if (isNetherPortal && env == World.Environment.NETHER) {
            destX = loc.getX() * 8.0;
            destZ = loc.getZ() * 8.0;
            targetServer = resolveOverworldServer(destX, destZ);
            targetEnvironment = World.Environment.NORMAL;
        } else if (!isNetherPortal && env == World.Environment.NORMAL) {
            targetServer = endServer;
            targetEnvironment = World.Environment.THE_END;
            destX = 0;
            destZ = 0;
        } else {
            return false; // End → Overworld handled by vanilla; ignore other causes
        }

        if (transferring.isPending(player.getUniqueId())) return true;
        transferring.mark(player.getUniqueId());
        PortalTransferHelper.transfer(api, jedisPool, player, destX, loc.getY(), destZ, targetEnvironment, targetServer);
        return true;
    }

    /**
     * Folia's async End-portal path never fires PlayerPortalEvent (PaperMC/Folia#389/#453),
     * so a player can land in a locally-generated End world on an overworld shard without
     * onPortal() ever seeing it. Catch that here, once the switch has already happened,
     * and forward them on to the real dedicated End server.
     */
    private void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) return;
        if (api.getServerRole() != ServerRole.OVERWORLD) return;

        Location loc = player.getLocation();
        PortalTransferHelper.transfer(api, jedisPool, player, 0, loc.getY(), 0, World.Environment.THE_END, endServer);
    }

    private void onJoin(PlayerJoinEvent event) {
        PortalTransferHelper.deliverPendingDestination(api, jedisPool, event.getPlayer());
    }

    private String resolveOverworldServer(double x, double z) {
        for (OverworldRegion region : overworldRegions) {
            if (region.contains(x, z)) return region.getServer();
        }
        return overworldRegions.isEmpty() ? "smp-spawn-1" : overworldRegions.get(0).getServer();
    }

}
