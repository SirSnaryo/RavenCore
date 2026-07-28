package com.kammoun.raven.settings.gui;

import com.kammoun.raven.library.gui.KMenu;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.item.ItemReader;
import com.kammoun.raven.library.item.MenuItem;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.settings.SettingsModule;
import com.kammoun.raven.settings.model.PlayerSettingDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class SettingsMenu extends KMenu {

    private final SettingsModule module;
    private final String enabledText;
    private final String disabledText;
    private final String updatedMessage;
    private final KSound toggleSound;
    private final Map<String, SettingItemTemplate> templates = new HashMap<>();

    public SettingsMenu(JavaPlugin plugin, SettingsModule module) {
        super(plugin, "settings/settings-menu.yml");
        this.module = module;
        this.enabledText = config.getString("Status.Enabled", "&#33FF66ON");
        this.disabledText = config.getString("Status.Disabled", "&#D90429OFF");
        this.updatedMessage = config.getString("Messages.Updated",
                "&#FFFFFF%setting% &#7A7A7Aset to %status%&#7A7A7A.");
        ConfigurationSection soundSection = config.getConfigurationSection("Sounds.Toggle");
        this.toggleSound = soundSection == null ? null : KSound.getSoundFromConfig(soundSection);
        loadTemplates();
    }

    @Override
    protected void setMenuItems(Player player, String... placeholders) {
        for (PlayerSettingDefinition definition : module.getRegistry().definitions()) {
            SettingItemTemplate template = templates.get(definition.id().toLowerCase());
            if (template == null) continue;
            MenuItem item = item(player, definition, template);
            if (item != null) setItem(item);
        }
    }

    private MenuItem item(Player player, PlayerSettingDefinition definition, SettingItemTemplate template) {
        boolean enabled = definition.value(player);
        MenuItem base = enabled ? template.enabled() : template.disabled();
        if (base == null) base = enabled ? template.disabled() : template.enabled();
        if (base == null || base.getSlot() < 0) return null;

        ItemStack rendered = PlaceholderHelper.parsePlaceholders(base.getItemStack(),
                "%setting%", ChatFormatter.strip(definition.displayName()),
                "%category%", definition.category(),
                "%status%", enabled ? enabledText : disabledText,
                "%enabled%", enabledText,
                "%disabled%", disabledText);
        return MenuItem.of(new ItemBuilder(rendered))
                .slot(base.getSlot())
                .permission(base.getPermission())
                .onClick(event -> toggle(player, definition, event));
    }

    private void toggle(Player player, PlayerSettingDefinition definition, InventoryClickEvent event) {
        boolean newValue = !definition.value(player);
        definition.set(player, newValue);
        open(player);
        if (toggleSound != null) {
            toggleSound.playSound(player);
        }
        player.sendMessage(ChatFormatter.color(PlaceholderHelper.parsePlaceholders(updatedMessage,
                "%setting%", ChatFormatter.strip(definition.displayName()),
                "%status%", newValue ? enabledText : disabledText,
                "%enabled%", enabledText,
                "%disabled%", disabledText)));
    }

    private void loadTemplates() {
        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) continue;
            String id = section.getString("setting", key);
            MenuItem enabled = readState(section, "enabled");
            MenuItem disabled = readState(section, "disabled");
            if (enabled == null && disabled == null) {
                enabled = ItemReader.readMenuItem(section, plugin);
            }
            templates.put(id.toLowerCase(), new SettingItemTemplate(enabled, disabled));
        }
    }

    private MenuItem readState(ConfigurationSection section, String state) {
        ConfigurationSection stateSection = section.getConfigurationSection(state);
        if (stateSection == null) return null;
        MenuItem item = ItemReader.readMenuItem(stateSection, plugin);
        if (item != null && item.getSlot() < 0 && section.contains("slot")) {
            item.slot(section.getInt("slot"));
        }
        return item;
    }

    private record SettingItemTemplate(MenuItem enabled, MenuItem disabled) {
    }
}
