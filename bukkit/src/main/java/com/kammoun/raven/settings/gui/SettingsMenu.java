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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsMenu extends KMenu {

    private static final Map<String, Integer> FALLBACK_SLOTS = Map.of("mob-spawn", 16);

    private final SettingsModule module;
    private final String enabledText;
    private final String disabledText;
    private final String updatedMessage;
    private final KSound toggleSound;
    private final Map<String, SettingItemTemplate> templates = new HashMap<>();

    public SettingsMenu(JavaPlugin plugin, SettingsModule module) {
        super(plugin, "settings/settings-menu.yml");
        this.module = module;
        this.enabledText = config.getString("Status.Enabled", "&#33FF66&lON");
        this.disabledText = config.getString("Status.Disabled", "&#FF4D4D&lOFF");
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
            if (template == null) {
                MenuItem fallback = fallback(player, definition);
                if (fallback != null) setItem(fallback);
                continue;
            }
            MenuItem item = item(player, definition, template);
            if (item != null) setItem(item);
        }
    }

    private MenuItem fallback(Player player, PlayerSettingDefinition definition) {
        Integer slot = FALLBACK_SLOTS.get(definition.id().toLowerCase());
        if (slot == null) return null;
        boolean enabled = definition.value(player);

        List<String> lore = new ArrayList<>();
        for (String line : definition.description()) lore.add(ChatFormatter.color("&f" + line));
        lore.add("");
        lore.add(ChatFormatter.color((enabled ? "&#55FF55✔" : "&#FF4D4D✕") + " &#AAAAAAStatus: "
                + (enabled ? enabledText : disabledText)));
        lore.add("");
        lore.add(ChatFormatter.color("&#FFFF55▸ &#FFFF55&nCLICK&r " + (enabled ? "&#55FF55" : "&#FF4D4D") + "to Toggle"));

        return MenuItem.of(new ItemBuilder(definition.material())
                .name(ChatFormatter.color((enabled ? "&#33FF66&l" : "&#FF4D4D&l")
                        + definition.displayName().toUpperCase()))
                .lore(lore))
                .slot(slot)
                .onClick(event -> toggle((Player) event.getWhoClicked(), definition, event));
    }

    private MenuItem item(Player player, PlayerSettingDefinition definition, SettingItemTemplate template) {
        boolean enabled = definition.value(player);
        MenuItem base = enabled ? template.enabled() : template.disabled();
        if (base == null) base = enabled ? template.disabled() : template.enabled();
        if (base == null || base.getSlot() < 0) return null;

        ItemStack rendered = PlaceholderHelper.parsePlaceholders(base.getItemStack(),
                "%setting%", ChatFormatter.strip(definition.displayName()),
                "%category%", definition.category(),
                "%status%", ChatFormatter.color(enabled ? enabledText : disabledText),
                "%enabled%", ChatFormatter.color(enabledText),
                "%disabled%", ChatFormatter.color(disabledText));
        return MenuItem.of(new ItemBuilder(rendered))
                .slot(base.getSlot())
                .permission(base.getPermission())
                .onClick(event -> toggle((Player) event.getWhoClicked(), definition, event));
    }

    private void toggle(Player player, PlayerSettingDefinition definition, InventoryClickEvent event) {
        boolean newValue = !definition.value(player);
        definition.set(player, newValue);
        refresh(player);
        if (toggleSound != null) {
            toggleSound.playSound(player);
        }
        player.sendMessage(ChatFormatter.color(PlaceholderHelper.parsePlaceholders(updatedMessage,
                "%setting%", ChatFormatter.strip(definition.displayName()),
                "%status%", ChatFormatter.color(newValue ? enabledText : disabledText),
                "%enabled%", ChatFormatter.color(enabledText),
                "%disabled%", ChatFormatter.color(disabledText))));
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
