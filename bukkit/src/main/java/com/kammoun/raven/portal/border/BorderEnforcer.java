package com.kammoun.raven.portal.border;

import com.kammoun.raven.portal.tracker.PendingTransferTracker;
import com.kammoun.raven.portal.helper.PortalTransferHelper;
import com.kammoun.raven.portal.config.PortalMessages;
import com.kammoun.raven.portal.model.ServerBorder;
import com.kammoun.raven.portal.model.ServerBorder.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPool;

import java.util.List;

/**
 * Hard-stops crossings into a neighbor server's territory — movement, teleports, vehicles,
 * and block interactions — and kicks off the handoff transfer once a crossing is detected.
 */
public class BorderEnforcer {

    private final JavaPlugin plugin;
    private final JedisPool jedisPool;
    private final List<ServerBorder> normalBorders;
    private final List<ServerBorder> netherBorders;
    private final String thisServer;
    private final PendingTransferTracker pendingTransfers;
    private final PortalMessages messages;

    public BorderEnforcer(JavaPlugin plugin, JedisPool jedisPool,
                           List<ServerBorder> normalBorders, List<ServerBorder> netherBorders,
                           String thisServer, PendingTransferTracker pendingTransfers, PortalMessages messages) {
        this.plugin = plugin;
        this.jedisPool = jedisPool;
        this.normalBorders = normalBorders;
        this.netherBorders = netherBorders;
        this.thisServer = thisServer;
        this.pendingTransfers = pendingTransfers;
        this.messages = messages;
    }

    /** The border set that applies to a world's dimension, or empty if that dimension isn't bordered. */
    List<ServerBorder> bordersFor(World.Environment env) {
        if (env == World.Environment.NORMAL) return normalBorders;
        if (env == World.Environment.NETHER) return netherBorders;
        return List.of();
    }

    public void triggerTransfer(Player player, Location loc, String targetServer) {
        if (pendingTransfers.isPending(player.getUniqueId())) return;
        pendingTransfers.mark(player.getUniqueId());
        // A crossing always lands the player in the same dimension on the neighbor server —
        // Overworld region borders hand off to another Overworld, Nether ones to another Nether.
        PortalTransferHelper.transfer(plugin, jedisPool, player,
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getEnvironment(), targetServer);
    }

    /**
     * The location to hand off to the neighbor: same as `current` except the border axis is
     * pushed past the line, so the player lands solidly inside the neighbor's territory
     * instead of right on it — landing right on the line would let the neighbor's own
     * enforcement immediately see them as still overshooting and bounce them straight back.
     */
    public Location arrivalLocation(Location current, ServerBorder border) {
        Location dest = current.clone();
        if (border.getAxis() == Axis.X) {
            dest.setX(border.arrivalCoord(thisServer));
        } else {
            dest.setZ(border.arrivalCoord(thisServer));
        }
        return dest;
    }

    public boolean isBeyondOwnSide(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        List<ServerBorder> borders = bordersFor(world.getEnvironment());
        if (borders.isEmpty()) return false;

        double x = loc.getX(), z = loc.getZ();
        for (ServerBorder border : borders) {
            if (border.overshoot(thisServer, x, z) > 0) return true;
        }
        return false;
    }

    /** Hard-stops crossings from ordinary movement (walking, sprinting, swimming, elytra). */
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to   = e.getTo();
        if (to == null) return;
        if (to.getX() == from.getX() && to.getZ() == from.getZ()) return; // look-only move

        Location corrected = enforce(e.getPlayer(), to);
        if (corrected != null) e.setTo(corrected);
    }

    /** Same hard stop for teleports (ender pearls, chorus fruit, /tp, warps, etc.). */
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null) return;

        Location corrected = enforce(e.getPlayer(), to);
        if (corrected != null) e.setTo(corrected);
    }

    /**
     * Riding a boat/minecart never fires PlayerMoveEvent for the rider — only
     * VehicleMoveEvent for the vehicle itself — so catch it here and snap the vehicle
     * (and its passengers) straight back.
     */
    public void onVehicleMove(VehicleMoveEvent e) {
        Location corrected = null;
        for (Entity passenger : e.getVehicle().getPassengers()) {
            if (!(passenger instanceof Player player)) continue;
            Location result = enforce(player, e.getTo());
            if (result != null) corrected = result;
        }
        if (corrected != null) e.getVehicle().teleport(corrected);
    }

    /** Blocks placing/breaking blocks (reach, buckets) past the border, even without crossing it bodily. */
    public void onBlockBreak(BlockBreakEvent e) {
        denyIfBeyondOwnSide(e.getBlock().getLocation(), e.getPlayer(), () -> e.setCancelled(true));
    }

    public void onBlockPlace(BlockPlaceEvent e) {
        denyIfBeyondOwnSide(e.getBlock().getLocation(), e.getPlayer(), () -> e.setCancelled(true));
    }

    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        denyIfBeyondOwnSide(e.getBlock().getLocation(), e.getPlayer(), () -> e.setCancelled(true));
    }

    public void onBucketFill(PlayerBucketFillEvent e) {
        denyIfBeyondOwnSide(e.getBlock().getLocation(), e.getPlayer(), () -> e.setCancelled(true));
    }

    /**
     * Blocks right/left-clicking a block past the border (opening chests, flipping levers, using
     * doors, etc.) — a player standing on their own side can still reach a few blocks across the line.
     * Interactions with no target block (clicking air) are left alone.
     */
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null) return;
        denyIfBeyondOwnSide(block.getLocation(), e.getPlayer(), () -> e.setCancelled(true));
    }

    private void denyIfBeyondOwnSide(Location loc, Player player, Runnable cancel) {
        if (isBeyondOwnSide(loc)) {
            cancel.run();
            messages.send(player, "border.deny-message");
        }
    }

    /**
     * Checks `to` against every border touching this server. If it has been pushed past
     * one, immediately kicks off the transfer and returns a clamped location on our own
     * side; otherwise returns null (no correction needed).
     */
    private Location enforce(Player player, Location to) {
        if (pendingTransfers.isPending(player.getUniqueId())) return null;

        World world = to.getWorld();
        if (world == null) return null;
        List<ServerBorder> borders = bordersFor(world.getEnvironment());
        if (borders.isEmpty()) return null;

        double x = to.getX(), z = to.getZ();
        Location corrected = null;

        for (ServerBorder border : borders) {
            if (border.overshoot(thisServer, x, z) <= 0) continue;

            triggerTransfer(player, arrivalLocation(to, border), border.otherServer(thisServer));

            if (corrected == null) corrected = to.clone();
            if (border.getAxis() == Axis.X) {
                corrected.setX(border.clampToOwnSide(thisServer, x));
            } else {
                corrected.setZ(border.clampToOwnSide(thisServer, z));
            }
        }

        return corrected;
    }
}
