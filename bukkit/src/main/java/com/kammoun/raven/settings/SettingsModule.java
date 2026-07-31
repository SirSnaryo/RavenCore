package com.kammoun.raven.settings;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.settings.command.SettingsCommand;
import com.kammoun.raven.settings.gui.SettingsMenu;
import com.kammoun.raven.settings.model.PlayerSettingDefinition;
import com.kammoun.raven.settings.model.PlayerSettingRegistry;
import com.kammoun.raven.tpa.model.TeleportPreference;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
public class SettingsModule extends KModule<RavenCore> {

    private final PlayerSettingRegistry registry = new PlayerSettingRegistry();
    private SettingsMenu menu;

    public SettingsModule() {
        super("settings");
    }

    @Override
    public void onModuleEnable() {
        saveDefaultConfig("settings-menu.yml");
        ensureEditableMenuItems();
        menu = new SettingsMenu(api, this);
        registerBuiltInSettings();
        registerCommand(new SettingsCommand(this), "settings");
        getLogger().info("[Settings] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        registry.clear();
        getLogger().info("[Settings] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        registry.clear();
        ensureEditableMenuItems();
        menu = new SettingsMenu(api, this);
        registerBuiltInSettings();
    }

    private void registerBuiltInSettings() {
        registerTeleportSettings();
        registerChatSettings();
        registerEconomySettings();
    }

    private void registerTeleportSettings() {
        if (api.getTeleportModule() == null || api.getTeleportModule().getSettingsService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("teleport.tpa")
                .category("Teleport")
                .displayName("TPA Requests")
                .description(List.of("Allow players to teleport to you.", "Controls incoming /tpa requests."))
                .material(Material.ENDER_PEARL)
                .priority(10)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).acceptsTpa())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.tpahere")
                .category("Teleport")
                .displayName("TPAHere Requests")
                .description(List.of("Allow players to pull you to them.", "Controls incoming /tpahere requests."))
                .material(Material.CLOCK)
                .priority(11)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).acceptsTpaHere())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA_HERE, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.auto")
                .category("Teleport")
                .displayName("TPAuto")
                .description(List.of("Automatically accept TPA requests.", "Use carefully near unknown players."))
                .material(Material.REDSTONE_TORCH)
                .priority(12)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).autoAccept())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA_AUTO, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.confirm-menu")
                .category("Teleport")
                .displayName("TPA Confirm Menu")
                .description(List.of("Show a menu before sending/accepting.", "Recommended for safer teleports."))
                .material(Material.ENDER_EYE)
                .priority(13)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).confirmMenu())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.CONFIRM_MENU, enabled))
                .build());
    }

    private void registerChatSettings() {
        if (api.getChatModule() == null || api.getChatModule().getSettingsService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("chat.public")
                .category("Chat")
                .displayName("Public Chat")
                .description(List.of("Show or hide member chat.", "You can still send messages."))
                .material(Material.OAK_SIGN)
                .priority(20)
                .getter(player -> api.getChatModule().getSettingsService().canReceiveChat(player.getUniqueId()))
                .setter((player, enabled) -> api.getChatModule().getSettingsService().setChat(player, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("chat.broadcast")
                .category("Chat")
                .displayName("Broadcasts")
                .description(List.of("Show or hide auto messages.", "Raven broadcasts still run on Redis."))
                .material(Material.BELL)
                .priority(21)
                .getter(player -> api.getChatModule().getSettingsService().canReceiveBroadcast(player.getUniqueId()))
                .setter((player, enabled) -> api.getChatModule().getSettingsService().setBroadcast(player, enabled))
                .build());
    }

    private void registerEconomySettings() {
        if (api.getEconomyModule() == null || api.getEconomyModule().getSettingsService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("economy.payments")
                .category("Economy")
                .displayName("Payments")
                .description(List.of("Allow players to send you money.", "Admins with bypass can still pay you."))
                .material(Material.EMERALD)
                .priority(40)
                .getter(player -> api.getEconomyModule().getSettingsService().acceptsPayments(player.getUniqueId()))
                .setter((player, enabled) -> api.getEconomyModule().getSettingsService().setPayments(player.getUniqueId(), enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("economy.messages")
                .category("Economy")
                .displayName("Pay Messages")
                .description(List.of("Show pay and shop economy messages.", "Transactions still log normally."))
                .material(Material.PAPER)
                .priority(41)
                .getter(player -> api.getEconomyModule().getSettingsService().paymentMessages(player.getUniqueId()))
                .setter((player, enabled) -> api.getEconomyModule().getSettingsService().setMessages(player.getUniqueId(), enabled))
                .build());
    }

    private void ensureEditableMenuItems() {
        File file = new File(getDataFolder(), "settings-menu.yml");
        if (!file.isFile()) return;
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        if (current.isConfigurationSection("Items")) return;

        try (InputStream input = api.getResource("settings/settings-menu.yml")) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection items = defaults.getConfigurationSection("Items");
            if (items == null) return;
            ConfigurationSection targetItems = current.createSection("Items");
            copySection(items, targetItems);
            current.set("Setting-Slots", null);
            current.save(file);
            getLogger().info("[Settings] Added editable setting item templates to settings-menu.yml.");
        } catch (IOException e) {
            getLogger().warning("[Settings] Could not update settings-menu.yml: " + e.getMessage());
        }
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                copySection(source.getConfigurationSection(key), target.createSection(key));
            } else {
                target.set(key, source.get(key));
            }
        }
    }
}
