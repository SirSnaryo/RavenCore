package com.kammoun.raven.mythictools.ability;

import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.economy.model.EconomyReason;
import com.kammoun.raven.mythictools.model.MythicToolDefinition;
import com.kammoun.raven.mythictools.model.MythicToolType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MythicToolAbilities {

    private static final int MAX_TREE_BLOCKS = 512;
    private final MythicToolsModule module;

    public MythicToolAbilities(MythicToolsModule module) {
        this.module = module;
    }

    public void drill(Player player, Block origin) {
        play(player, MythicToolType.DRILL);
        origin.getWorld().spawnParticle(Particle.FLAME, origin.getLocation().add(.5, .5, .5),
                30, .5, .5, .5, .05);
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block block : area(origin, player)) {
            if (module.config().mineable(block.getType()) || module.config().diggable(block.getType())) {
                block.breakNaturally(tool);
            }
        }
    }

    public void treeChopper(Player player, Block origin) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        ArrayDeque<Block> pending = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        pending.add(origin);
        play(player, MythicToolType.TREE_CHOPPER);
        while (!pending.isEmpty() && visited.size() < MAX_TREE_BLOCKS) {
            Block block = pending.removeFirst();
            if (!visited.add(block) || !module.config().log(block.getType())) continue;
            block.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, block.getLocation().add(.5, .5, .5),
                    8, .25, .25, .25, .05, block.getBlockData());
            block.breakNaturally(tool);
            pending.add(block.getRelative(1, 0, 0));
            pending.add(block.getRelative(-1, 0, 0));
            pending.add(block.getRelative(0, 1, 0));
            pending.add(block.getRelative(0, -1, 0));
            pending.add(block.getRelative(0, 0, 1));
            pending.add(block.getRelative(0, 0, -1));
        }
    }

    public void sellChest(Player player, Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            module.send(player, "tool.sell-axe.chest-only");
            return;
        }
        if (!module.sellBridge().available() || module.getApi().getEconomyModule() == null) {
            module.send(player, "tool.sell-axe.unavailable");
            return;
        }

        Inventory inventory = chest.getInventory();
        double total = 0D;
        int sold = 0;
        List<Integer> soldSlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR || !module.sellBridge().sellable(item)) continue;
            double earned;
            try {
                var shopModule = module.getApi().getShopModule();
                if (shopModule == null) {
                    module.send(player, "tool.sell-axe.unavailable");
                    return;
                }
                double maximumEarnings = shopModule.shopPriceGuard().maximumEarnings(
                        player.getUniqueId(), item, item.getAmount());
                earned = module.sellBridge().record(
                        player.getUniqueId(), item, item.getAmount(), maximumEarnings);
            } catch (RuntimeException e) {
                module.getLogger().warning("[MythicTools] RavenSell rejected a Sell Axe transaction for "
                        + player.getName() + ": " + e.getMessage());
                module.send(player, "tool.sell-axe.unavailable");
                return;
            }
            if (earned < 0D) continue;
            total += earned;
            sold += item.getAmount();
            soldSlots.add(slot);
        }
        if (total <= 0D) {
            module.send(player, "tool.sell-axe.empty");
            return;
        }
        boolean deposited = module.getApi().getEconomyModule().getEconomyService().deposit(
                player.getUniqueId(),
                total,
                EconomyReason.system("Mythic Sell Axe chest sale"),
                "MythicTools",
                null,
                player.getName()
        ).success();
        if (!deposited) {
            module.getLogger().severe("[MythicTools] RavenSell recorded a chest sale but Economy rejected the "
                    + "deposit for " + player.getName() + ". Amount: " + total);
            module.send(player, "tool.sell-axe.deposit-failed");
            return;
        }
        soldSlots.forEach(slot -> inventory.setItem(slot, null));
        module.send(player, "tool.sell-axe.success",
                "%items%", Integer.toString(sold), "%money%", String.format(java.util.Locale.US, "%,.2f", total));
    }

    private void play(Player player, MythicToolType type) {
        MythicToolDefinition definition = module.config().tool(type);
        if (definition != null && definition.sound() != null) definition.sound().playSound(player);
    }

    private List<Block> area(Block center, Player player) {
        List<Block> blocks = new ArrayList<>(9);
        Location location = center.getLocation();
        BlockFace face = targetFace(player);
        int xMin = -1, xMax = 1, yMin = -1, yMax = 1, zMin = -1, zMax = 1;
        switch (face) {
            case UP, DOWN -> { yMin = 0; yMax = 0; }
            case NORTH, SOUTH -> { zMin = 0; zMax = 0; }
            case EAST, WEST -> { xMin = 0; xMax = 0; }
            default -> {
                if (Math.abs(player.getPitch()) > 45) {
                    yMin = 0; yMax = 0;
                } else if ((player.getYaw() >= -45 && player.getYaw() < 45)
                        || player.getYaw() >= 135 || player.getYaw() < -135) {
                    zMin = 0; zMax = 0;
                } else {
                    xMin = 0; xMax = 0;
                }
            }
        }
        for (int x = xMin; x <= xMax; x++)
            for (int y = yMin; y <= yMax; y++)
                for (int z = zMin; z <= zMax; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) blocks.add(block);
                }
        return blocks;
    }

    private BlockFace targetFace(Player player) {
        RayTraceResult result = player.rayTraceBlocks(5D);
        return result != null && result.getHitBlockFace() != null ? result.getHitBlockFace() : BlockFace.SELF;
    }
}
