package com.kammoun.raven.settings.gui;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.settings.SettingsModule;
import com.kammoun.raven.settings.model.PlayerSettingDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SettingsMenu extends KMenu {

    private final SettingsModule module;
    private final List<Integer> settingSlots;
    private final String enabledText;
    private final String disabledText;
    private final String updatedMessage;
    private final KSound toggleSound;

    public SettingsMenu(JavaPlugin plugin, SettingsModule module) {
        super(plugin, "settings/settings-menu.yml");
        this.module = module;
        this.settingSlots = loadSlots();
        this.enabledText = config.getString("Status.Enabled", "&#33FF66ON");
        this.disabledText = config.getString("Status.Disabled", "&#D90429OFF");
        this.updatedMessage = config.getString("Messages.Updated",
                "&#33FF66Updated &#FFFFFF%setting% &#7A7A7Ato %status%&#7A7A7A.");
        ConfigurationSection soundSection = config.getConfigurationSection("Sounds.Toggle");
        this.toggleSound = soundSection == null ? null : KSound.getSoundFromConfig(soundSection);
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        List<PlayerSettingDefinition> definitions = module.getRegistry().definitions();
        int count = Math.min(definitions.size(), settingSlots.size());
        for (int i = 0; i < count; i++) {
            PlayerSettingDefinition definition = definitions.get(i);
            setItem(item(player, definition, settingSlots.get(i)));
        }
    }

    private MenuItem item(Player player, PlayerSettingDefinition definition, int slot) {
        boolean enabled = definition.value(player);
        List<String> lore = new ArrayList<>();
        lore.add("&#7A7A7A" + smallCaps(definition.category()));
        lore.addAll(definition.description());
        lore.add("");
        lore.add("&#FFFFFFCurrently: " + (enabled ? enabledText : disabledText));
        lore.add("&#7A7A7AClick to toggle.");

        Material material = enabled ? definition.material() : Material.GRAY_DYE;
        return MenuItem.of(new ItemBuilder(material)
                        .name(definition.displayName())
                        .lore(lore))
                .slot(slot)
                .onClick(event -> toggle(player, definition, event));
    }

    private void toggle(Player player, PlayerSettingDefinition definition, InventoryClickEvent event) {
        boolean newValue = !definition.value(player);
        definition.set(player, newValue);
        event.getView().getTopInventory().setItem(event.getSlot(), item(player, definition, event.getSlot()).getItemStack());
        player.updateInventory();
        if (toggleSound != null) {
            toggleSound.playSound(player);
        }
        player.sendMessage(ChatFormatter.color(updatedMessage
                .replace("%setting%", ChatFormatter.strip(definition.displayName()))
                .replace("%status%", newValue ? enabledText : disabledText)));
    }

    private List<Integer> loadSlots() {
        List<Integer> slots = config.getIntegerList("Setting-Slots");
        if (!slots.isEmpty()) {
            return slots;
        }
        return List.of(38, 39, 40, 30, 29);
    }

    private String smallCaps(String value) {
        StringBuilder builder = new StringBuilder();
        for (char c : value.toLowerCase().toCharArray()) {
            builder.append(switch (c) {
                case 'a' -> 'ᴀ';
                case 'b' -> 'ʙ';
                case 'c' -> 'ᴄ';
                case 'd' -> 'ᴅ';
                case 'e' -> 'ᴇ';
                case 'f' -> 'ғ';
                case 'g' -> 'ɢ';
                case 'h' -> 'ʜ';
                case 'i' -> 'ɪ';
                case 'j' -> 'ᴊ';
                case 'k' -> 'ᴋ';
                case 'l' -> 'ʟ';
                case 'm' -> 'ᴍ';
                case 'n' -> 'ɴ';
                case 'o' -> 'ᴏ';
                case 'p' -> 'ᴘ';
                case 'q' -> 'ǫ';
                case 'r' -> 'ʀ';
                case 's' -> 's';
                case 't' -> 'ᴛ';
                case 'u' -> 'ᴜ';
                case 'v' -> 'ᴠ';
                case 'w' -> 'ᴡ';
                case 'x' -> 'x';
                case 'y' -> 'ʏ';
                case 'z' -> 'ᴢ';
                default -> c;
            });
        }
        return builder.toString();
    }
}
