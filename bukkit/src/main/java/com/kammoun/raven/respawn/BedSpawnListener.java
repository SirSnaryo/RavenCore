package com.kammoun.raven.respawn;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.respawn.db.BedSpawnRepository;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BedSpawnListener implements Listener {
    private final RavenCore plugin;
    private final BedSpawnRepository repository;

    public BedSpawnListener(RavenCore plugin, BedSpawnRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        BedSpawnLocator.canonicalBed(event.getBed()).ifPresent(bed -> {
            BedSpawnRecord record = new BedSpawnRecord(player.getUniqueId(), plugin.getServerId(),
                    bed.getWorld().getName(), bed.getX(), bed.getY(), bed.getZ());
            SchedulerUtil.runAsync(() -> {
                try {
                    repository.upsert(record);
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().warning("[Respawn] Failed to save bed for " + player.getName() + ": " + e.getMessage());
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { invalidate(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { invalidate(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) { invalidate(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { invalidate(event.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) { invalidate(event.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { invalidate(event.getBlocks()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { invalidate(event.getBlocks()); }

    private void invalidate(Block block) {
        BedSpawnLocator.canonicalBed(block).ifPresent(this::deleteAt);
    }

    private void invalidate(Collection<Block> blocks) {
        Set<Block> beds = new LinkedHashSet<>();
        for (Block block : blocks) BedSpawnLocator.canonicalBed(block).ifPresent(beds::add);
        beds.forEach(this::deleteAt);
    }

    private void deleteAt(Block bed) {
        String server = plugin.getServerId();
        String world = bed.getWorld().getName();
        int x = bed.getX(), y = bed.getY(), z = bed.getZ();
        SchedulerUtil.runAsync(() -> {
            try {
                repository.deleteAt(server, world, x, y, z);
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().warning("[Respawn] Failed to invalidate destroyed bed: " + e.getMessage());
            }
        });
    }
}
