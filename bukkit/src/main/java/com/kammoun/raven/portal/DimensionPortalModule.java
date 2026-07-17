package com.kammoun.raven.portal;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.ServerRole;
import com.kammoun.raven.portal.helper.PortalTransferHelper;
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

public class DimensionPortalModule extends KModule<RavenCore> {

    /** How long a transfer is given to complete before portal detection resumes for that player. */
    private static final long TRANSFER_TIMEOUT_MILLIS = 5000L;

    private JedisPool jedisPool;
    private String endServer;

    private PendingTransferTracker transferring;

    public DimensionPortalModule() {
        super("portal");
    }

    @Override
    public void onModuleEnable() {
        jedisPool    = api.getRedisMessenger().getConnection().getPool();
        endServer    = getConfig().getString("servers.end", "smp-end");
        transferring = new PendingTransferTracker(TRANSFER_TIMEOUT_MILLIS);

        // The Nether is now local to each survival server (a plain vanilla dimension change —
        // this module doesn't touch it), so only the End still needs cross-server handling.
        // PlayerPortalEvent only fires if the server would actually attempt the vanilla
        // dimension change. On these dedicated-End setups allow-end is off (no local End is
        // wanted), so vanilla never tries and the event never fires. Detect standing in the
        // portal block directly instead, and keep the event listener as a no-op safety net for
        // any server where vanilla does still attempt it.
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
        if (event.getCause() != TeleportCause.END_PORTAL) return;

        if (attemptEndTransfer(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
        }
    }

    /** Primary detection: fires on every step, so it catches standing in a portal even when vanilla never would. */
    private void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        if (to.getBlock().getType() != Material.END_PORTAL) return;

        attemptEndTransfer(event.getPlayer(), to);
    }

    /**
     * Resolves the cross-server End destination for a player standing in an end portal and
     * kicks off the transfer, unless one is already in flight for this crossing. Returns true
     * if this is (or already was) a recognized portal crossing, so vanilla teleport can be
     * safely cancelled by callers that have an event to cancel.
     */
    private boolean attemptEndTransfer(Player player, Location loc) {
        if (loc.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return false; // End → Overworld handled by vanilla; ignore other causes
        }

        if (transferring.isPending(player.getUniqueId())) return true;
        transferring.mark(player.getUniqueId());
        PortalTransferHelper.transfer(api, jedisPool, player, 0, loc.getY(), 0, World.Environment.THE_END, endServer);
        return true;
    }

    /**
     * Folia's async End-portal path never fires PlayerPortalEvent (PaperMC/Folia#389/#453),
     * so a player can land in a locally-generated End world on a survival shard without
     * onPortal() ever seeing it. Catch that here, once the switch has already happened,
     * and forward them on to the real dedicated End server.
     */
    private void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) return;
        if (api.getServerRole() != ServerRole.SURVIVAL) return;

        Location loc = player.getLocation();
        PortalTransferHelper.transfer(api, jedisPool, player, 0, loc.getY(), 0, World.Environment.THE_END, endServer);
    }

    private void onJoin(PlayerJoinEvent event) {
        PortalTransferHelper.deliverPendingDestination(api, jedisPool, event.getPlayer());
    }

}
