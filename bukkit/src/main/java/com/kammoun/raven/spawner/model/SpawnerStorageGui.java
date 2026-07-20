package com.kammoun.raven.spawner.model;

import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Storage GUI layout for a single spawner type, loaded from that type's own file
 * (e.g. spawners/blaze.yml). Each spawner type owns its own title, size and buttons —
 * there is no shared template across types.
 */
@Getter
public class SpawnerStorageGui {

    private static final String DEFAULT_TITLE = "&#FFD447&l%type% &8| &#FFFFFFStorage (%amount%x)";
    private static final int DEFAULT_SIZE = 54;

    private final int size;
    private final String title;
    private final MenuItem prevPage;
    private final MenuItem nextPage;
    private final MenuItem dropAll;
    private final MenuItem sellAll;
    private final MenuItem spawnerIcon;
    private final MenuItem filler;

    public SpawnerStorageGui(int size, String title, MenuItem prevPage, MenuItem nextPage,
                              MenuItem dropAll, MenuItem sellAll, MenuItem spawnerIcon, MenuItem filler) {
        this.size = size;
        this.title = title;
        this.prevPage = prevPage;
        this.nextPage = nextPage;
        this.dropAll = dropAll;
        this.sellAll = sellAll;
        this.spawnerIcon = spawnerIcon;
        this.filler = filler;
    }

    public static SpawnerStorageGui fromConfig(ConfigurationSection section, JavaPlugin plugin) {
        if (section == null) {
            return new SpawnerStorageGui(DEFAULT_SIZE, DEFAULT_TITLE, null, null, null, null, null, null);
        }

        int size = section.getInt("Size", DEFAULT_SIZE);
        String title = section.getString("Title", DEFAULT_TITLE);

        MenuItem filler = ItemReader.readMenuItem(section.getConfigurationSection("Navigation.Filler-Item"), plugin);

        ConfigurationSection items = section.getConfigurationSection("Items");
        MenuItem prevPage = items != null ? ItemReader.readMenuItem(items.getConfigurationSection("Prev-Page"), plugin) : null;
        MenuItem nextPage = items != null ? ItemReader.readMenuItem(items.getConfigurationSection("Next-Page"), plugin) : null;
        MenuItem dropAll = items != null ? ItemReader.readMenuItem(items.getConfigurationSection("Drop-All"), plugin) : null;
        MenuItem sellAll = items != null ? ItemReader.readMenuItem(items.getConfigurationSection("Sell-All"), plugin) : null;
        MenuItem spawnerIcon = items != null ? ItemReader.readMenuItem(items.getConfigurationSection("Spawner-Icon"), plugin) : null;

        return new SpawnerStorageGui(size, title, prevPage, nextPage, dropAll, sellAll, spawnerIcon, filler);
    }

    public int prevPageSlot() { return prevPage != null ? prevPage.getSlot() : -1; }
    public int nextPageSlot() { return nextPage != null ? nextPage.getSlot() : -1; }
    public int dropAllSlot() { return dropAll != null ? dropAll.getSlot() : -1; }
    public int sellAllSlot() { return sellAll != null ? sellAll.getSlot() : -1; }
    public int iconSlot() { return spawnerIcon != null ? spawnerIcon.getSlot() : -1; }
}
