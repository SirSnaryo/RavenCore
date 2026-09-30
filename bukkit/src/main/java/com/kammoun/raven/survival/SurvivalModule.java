package com.kammoun.raven.survival;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.survival.listener.SurvivalListener;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SurvivalModule extends KModule<RavenCore> {

    private volatile boolean hungerDisabled;
    private volatile int foodLevel;
    private volatile float saturation;
    private volatile Set<String> ignoredWorlds = Set.of();

    public SurvivalModule() {
        super("survival");
    }

    @Override
    public void onModuleEnable() {
        readConfig();
        registerEvent(new SurvivalListener(this));
        if (hungerDisabled) {
            getLogger().info("[Survival] Hunger loss disabled (food level " + foodLevel + ").");
        } else {
            getLogger().info("[Survival] Hunger loss is enabled (vanilla behaviour).");
        }
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("[Survival] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        readConfig();
        getLogger().info("[Survival] Config reloaded; hunger loss "
                + (hungerDisabled ? "disabled" : "enabled") + ".");
    }

    private void readConfig() {
        hungerDisabled = getConfig().getBoolean("hunger.disable-hunger", true);
        foodLevel = Math.max(0, Math.min(20, getConfig().getInt("hunger.food-level", 20)));
        saturation = (float) Math.max(0D, Math.min(20D, getConfig().getDouble("hunger.saturation", 20D)));

        Set<String> worlds = new HashSet<>();
        ConfigurationSection section = getConfig().getConfigurationSection("hunger.ignored-worlds");
        if (section != null) {
            for (String world : section.getKeys(false)) worlds.add(normalise(world));
        } else {
            for (String world : getConfig().getStringList("hunger.ignored-worlds")) worlds.add(normalise(world));
        }
        ignoredWorlds = Set.copyOf(worlds);
    }

    private static String normalise(String world) {
        return world.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isHungerDisabled() {
        return hungerDisabled;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }

    public boolean isWorldIgnored(String worldName) {
        return ignoredWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
