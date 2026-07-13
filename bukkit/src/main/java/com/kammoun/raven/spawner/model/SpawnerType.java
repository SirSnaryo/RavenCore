package com.kammoun.raven.spawner.model;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.util.NBTHelper;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a spawner type, loaded from its own file under spawners/ (e.g. spawners/blaze.yml).
 * One instance per type key (e.g. "COW"), shared across all placed stacks of that type.
 */
@Getter
public class SpawnerType {

    private final String id;
    private final String displayName;
    private final MenuItem spawnerItem;
    private final MenuItem guiIcon;
    private final SpawnerStorageGui storageGui;
    private final int spawnIntervalSeconds;
    private final int xpPerCycle;
    private final int maxStorage;
    private final List<SpawnerDrop> drops;

    public SpawnerType(String id, String displayName, MenuItem spawnerItem, MenuItem guiIcon,
                       SpawnerStorageGui storageGui, int spawnIntervalSeconds, int xpPerCycle,
                       int maxStorage, List<SpawnerDrop> drops) {
        this.id = id;
        this.displayName = displayName;
        this.spawnerItem = spawnerItem;
        this.guiIcon = guiIcon;
        this.storageGui = storageGui;
        this.spawnIntervalSeconds = spawnIntervalSeconds;
        this.xpPerCycle = xpPerCycle;
        this.maxStorage = maxStorage;
        this.drops = drops;
    }

    public static SpawnerType fromConfig(String id, ConfigurationSection section, JavaPlugin plugin) {
        String displayName = section.getString("display-name", id);

        MenuItem spawnerItem = ItemReader.readMenuItem(section.getConfigurationSection("spawner-item"), plugin);
        MenuItem guiIcon = ItemReader.readMenuItem(section.getConfigurationSection("gui-icon"), plugin);
        SpawnerStorageGui storageGui = SpawnerStorageGui.fromConfig(section.getConfigurationSection("storage-gui"), plugin);

        int interval = section.getInt("spawn-interval-seconds", 30);
        int xp = section.getInt("xp-per-cycle", 0);
        int maxStorage = section.getInt("max-storage", 10000);

        List<SpawnerDrop> drops = new ArrayList<>();
        List<?> dropList = section.getList("drops", List.of());
        for (Object obj : dropList) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            String matName = (String) map.get("material");
            if (matName == null) continue;
            try {
                Material mat = Material.valueOf(matName.toUpperCase());
                int amount = map.get("amount-per-spawner") instanceof Number n ? n.intValue() : 1;
                double price = map.get("sell-price") instanceof Number n ? n.doubleValue() : 0.0;
                drops.add(new SpawnerDrop(mat, amount, price));
            } catch (IllegalArgumentException ignored) {}
        }

        return new SpawnerType(id.toUpperCase(), displayName, spawnerItem, guiIcon,
                storageGui, interval, xp, maxStorage, drops);
    }

    /**
     * Creates a spawner item for this type, encoding the given stack size in NBT.
     */
    public ItemStack createItem(int stackSize) {
        if (spawnerItem == null) {
            ItemStack item = new ItemStack(Material.SPAWNER, 1);
            item = NBTHelper.setString(item, "spawner-type", id);
            item = NBTHelper.setInt(item, "spawner-stack", stackSize);
            return item;
        }
        ItemStack item = spawnerItem.getItemStack();
        item = NBTHelper.setString(item, "spawner-type", id);
        item = NBTHelper.setInt(item, "spawner-stack", stackSize);
        return item;
    }

    /**
     * Returns materials in declaration order (used for GUI display order).
     */
    public List<Material> getDropMaterials() {
        List<Material> mats = new ArrayList<>(drops.size());
        for (SpawnerDrop drop : drops) mats.add(drop.getMaterial());
        return mats;
    }
}
