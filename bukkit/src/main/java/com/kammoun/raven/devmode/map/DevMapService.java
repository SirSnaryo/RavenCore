package com.kammoun.raven.devmode.map;

import com.kammoun.raven.library.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

public class DevMapService {

    private final JavaPlugin plugin;
    private final long updateMs;
    private final Map<PerformanceMapRenderer.Type, MapView> mapViews = new EnumMap<>(PerformanceMapRenderer.Type.class);

    public DevMapService(JavaPlugin plugin, long updateMs) {
        this.plugin = plugin;
        this.updateMs = updateMs;
    }

    public ItemStack createPerformanceMap(Player player) {
        return createMap(player, PerformanceMapRenderer.Type.PERFORMANCE,
                "&#A855F7Performance Chart",
                "&#FFFFFFLive TPS/MSPT chart.");
    }

    public ItemStack createMemoryMap(Player player) {
        return createMap(player, PerformanceMapRenderer.Type.MEMORY,
                "&#33FF66Memory Chart",
                "&#FFFFFFLive heap and non-heap chart.");
    }

    public ItemStack createNetworkMap(Player player) {
        return createMap(player, PerformanceMapRenderer.Type.NETWORK,
                "&#4CC9F0Network Chart",
                "&#FFFFFFLive ping and online chart.");
    }

    private ItemStack createMap(Player player, PerformanceMapRenderer.Type type, String name, String description) {
        MapView mapView = mapView(player, type);

        ItemStack item = new ItemBuilder(Material.FILLED_MAP)
                .name(name)
                .lore("&#7A7A7ADEVMODE", "", description)
                .build();
        if (item.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(mapView);
            item.setItemMeta(meta);
        }
        return item;
    }

    private synchronized MapView mapView(Player player, PerformanceMapRenderer.Type type) {
        MapView existing = mapViews.get(type);
        if (existing != null) return existing;

        World world = player.getWorld();
        MapView mapView = Bukkit.createMap(world);
        mapView.getRenderers().clear();
        mapView.addRenderer(new PerformanceMapRenderer(plugin, type, updateMs));
        mapViews.put(type, mapView);
        return mapView;
    }

    public synchronized void clear() {
        mapViews.values().forEach(view -> view.getRenderers()
                .removeIf(renderer -> renderer instanceof PerformanceMapRenderer));
        mapViews.clear();
    }
}
