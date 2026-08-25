package com.kammoun.raven.borders.world;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.borders.config.TrueWorldBorderConfig;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class TrueWorldBorderController {
    private final RavenCore plugin;
    private final Logger logger;
    private final Set<UUID> appliedWorlds = ConcurrentHashMap.newKeySet();
    private volatile TrueWorldBorderConfig config;

    public TrueWorldBorderController(RavenCore plugin, Logger logger, TrueWorldBorderConfig config) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
    }

    public void applyLoadedWorlds() {
        if (!config.enabled()) return;
        for (World world : plugin.getServer().getWorlds()) {
            apply(world);
        }
    }

    public void apply(World world) {
        TrueWorldBorderConfig current = config;
        if (!current.enabled() || world.getEnvironment() != World.Environment.NORMAL) return;

        WorldBorder border = world.getWorldBorder();
        border.setCenter(current.centerX(), current.centerZ());
        border.setSize(current.hugeSize());
        border.setDamageAmount(current.damageAmount());
        border.setDamageBuffer(current.damageBuffer());
        border.setWarningDistance(current.warningDistance());
        border.setWarningTime(current.warningTime());
        appliedWorlds.add(world.getUID());

        logger.info("[WorldBorder] Applied true map edge to '" + world.getName()
                + "': edges=[" + String.join(", ", current.configuredEdges()) + "]"
                + ", center=(" + current.centerX() + "," + current.centerZ() + ")"
                + ", size=" + current.hugeSize());
    }

    public void reload(TrueWorldBorderConfig updated) {
        TrueWorldBorderConfig previous = config;
        config = updated;
        if (previous.enabled() && !updated.enabled()) {
            resetAppliedWorlds();
            logger.info("[WorldBorder] Disabled and reset borders applied during this server session.");
            return;
        }
        if (updated.enabled()) {
            applyLoadedWorlds();
        }
    }

    public void close() {
        appliedWorlds.clear();
    }

    private void resetAppliedWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL && appliedWorlds.remove(world.getUID())) {
                world.getWorldBorder().reset();
            }
        }
    }
}
