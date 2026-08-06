package com.kammoun.raven.crates.service;

import com.kammoun.raven.crates.model.CrateDefinition;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;

public final class CrateHologramService {
    public void createHolograms(Location location, CrateDefinition crate) {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) return;
        if (crate.holograms() == null || crate.holograms().isEmpty()) return;
        try {
            int index = 0;
            for (CrateDefinition.HologramConfig config : crate.holograms()) {
                String name = hologramName(location, index);
                removeHologram(name);
                Location holoLoc = location.clone().add(0.5, config.height(), 0.5);
                TextHologramData data = new TextHologramData(name, holoLoc);
                data.setPersistent(false);
                data.setText(config.lines());
                data.setTextUpdateInterval(20);
                data.setBackground(Color.fromARGB(0));
                de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
                Hologram hologram = manager.create(data);
                manager.addHologram(hologram);
                index++;
            }
        } catch (Throwable ignored) {
        }
    }

    public void removeHolograms(Location location) {
        for (int i = 0; i < 10; i++) removeHologram(hologramName(location, i));
    }

    public int cleanupAllCrateHolograms() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) return 0;
        int removed = 0;
        try {
            de.oliver.fancyholograms.api.HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            for (Hologram hologram : manager.getHolograms()) {
                if (hologram.getData().getName().startsWith("crate_")) {
                    manager.removeHologram(hologram);
                    removed++;
                }
            }
        } catch (Throwable ignored) {
        }
        return removed;
    }

    private void removeHologram(String name) {
        try {
            FancyHologramsPlugin.get().getHologramManager()
                    .getHologram(name).ifPresent(FancyHologramsPlugin.get().getHologramManager()::removeHologram);
        } catch (Throwable ignored) {
        }
    }

    private String hologramName(Location loc, int index) {
        return "crate_" + loc.getWorld().getName() + "_"
                + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ() + "_" + index;
    }
}
