package com.kammoun.raven.settings;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.settings.command.SettingsCommand;
import com.kammoun.raven.settings.gui.SettingsMenu;
import com.kammoun.raven.settings.model.PlayerSettingDefinition;
import com.kammoun.raven.settings.model.PlayerSettingRegistry;
import com.kammoun.raven.teleport.model.TeleportPreference;
import lombok.Getter;
import org.bukkit.Material;

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
        menu = new SettingsMenu(api, this);
        registerBuiltInSettings();
    }

    private void registerBuiltInSettings() {
        registerTeleportSettings();
        registerScoreboardSettings();
    }

    private void registerTeleportSettings() {
        if (api.getTeleportModule() == null || api.getTeleportModule().getSettingsService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("teleport.tpa")
                .category("Teleport")
                .displayName("&#33FF66ᴛᴘᴀ ʀᴇǫᴜᴇsᴛs")
                .description(List.of("&#FFFFFFAllow players to request teleporting to you."))
                .material(Material.ENDER_PEARL)
                .priority(10)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).acceptsTpa())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.tpahere")
                .category("Teleport")
                .displayName("&#4CC9F0ᴛᴘᴀʜᴇʀᴇ ʀᴇǫᴜᴇsᴛs")
                .description(List.of("&#FFFFFFAllow players to ask you to teleport to them."))
                .material(Material.ENDER_EYE)
                .priority(11)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).acceptsTpaHere())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA_HERE, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.auto")
                .category("Teleport")
                .displayName("&#FFD447ᴛᴘᴀᴜᴛᴏ")
                .description(List.of("&#FFFFFFAutomatically accept incoming teleport requests."))
                .material(Material.CLOCK)
                .priority(12)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).autoAccept())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA_AUTO, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.confirm-menu")
                .category("Teleport")
                .displayName("&#FF2B2Bᴛᴘᴀ ᴄᴏɴғɪʀᴍ ᴍᴇɴᴜ")
                .description(List.of("&#FFFFFFShow confirm GUIs before sending or accepting TPA."))
                .material(Material.COMPASS)
                .priority(13)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).confirmMenu())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.CONFIRM_MENU, enabled))
                .build());
    }

    private void registerScoreboardSettings() {
        if (api.getScoreboardModule() == null || api.getScoreboardModule().getService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("scoreboard.enabled")
                .category("Visual")
                .displayName("&#3A86FFsᴄᴏʀᴇʙᴏᴀʀᴅ")
                .description(List.of("&#FFFFFFShow or hide your personal scoreboard."))
                .material(Material.MAP)
                .priority(30)
                .getter(player -> api.getScoreboardModule().getService().isEnabled(player))
                .setter((player, enabled) -> api.getScoreboardModule().getService().setEnabled(player, enabled))
                .build());
    }
}
