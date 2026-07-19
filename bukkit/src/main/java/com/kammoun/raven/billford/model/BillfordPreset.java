package com.kammoun.raven.billford.model;

import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.ItemWriter;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * A single Billford trade: the items a player must hand in ({@link #requiredItems})
 * and the item they receive in return ({@link #tradeItem}).
 *
 * <p>Presets are network-wide, so their item data is persisted to MySQL as a YAML blob.
 * {@link #serializeData()} / {@link #deserialize} round-trip the items through the shared
 * {@link ItemWriter}/{@link ItemReader} so full meta (names, lore, enchants, skulls, NBT)
 * survives the trip — the {@code enabled} flag lives in its own column, not the blob.
 */
@Getter
@Setter
public class BillfordPreset {

    private final String name;
    private List<ItemStack> requiredItems = new ArrayList<>();
    private ItemStack tradeItem;
    private boolean enabled = true;

    public BillfordPreset(String name) {
        this.name = name;
    }

    public boolean isValid() {
        return !requiredItems.isEmpty() && tradeItem != null;
    }

    public int getRequiredItemsCount() {
        return requiredItems.size();
    }

    /** Serializes the items (not {@code enabled}) to a YAML string for the {@code data} column. */
    public String serializeData() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection required = yaml.createSection("required-items");
        for (int i = 0; i < requiredItems.size(); i++) {
            ItemWriter.writeItem(required, "item-" + i, requiredItems.get(i));
        }
        if (tradeItem != null) {
            ItemWriter.writeItem(yaml, "trade-item", tradeItem);
        }
        return yaml.saveToString();
    }

    public static BillfordPreset deserialize(String name, boolean enabled, String data, JavaPlugin plugin) {
        BillfordPreset preset = new BillfordPreset(name);
        preset.setEnabled(enabled);

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(data == null ? "" : data);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().warning("[Billford] Corrupt preset data for '" + name + "': " + e.getMessage());
            return preset;
        }

        ConfigurationSection required = yaml.getConfigurationSection("required-items");
        if (required != null) {
            for (String key : required.getKeys(false)) {
                ItemBuilder builder = ItemReader.readItem(required.getConfigurationSection(key), plugin);
                if (builder != null) preset.requiredItems.add(builder.build());
            }
        }

        if (yaml.isConfigurationSection("trade-item")) {
            ItemBuilder builder = ItemReader.readItem(yaml.getConfigurationSection("trade-item"), plugin);
            if (builder != null) preset.tradeItem = builder.build();
        }

        return preset;
    }
}
