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
        registerChatSettings();
        registerEconomySettings();
        registerScoreboardSettings();
    }

    private void registerTeleportSettings() {
        if (api.getTeleportModule() == null || api.getTeleportModule().getSettingsService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("teleport.tpa")
                .category("Teleport")
                .displayName("&#33FF66ᴛᴘᴀ ʀᴇǫᴜᴇsᴛs")
                .description(List.of("&#FFFFFFAllow players to teleport to you.", "&#7A7A7AControls incoming /tpa requests."))
                .material(Material.ENDER_PEARL)
                .priority(10)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).acceptsTpa())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.tpahere")
                .category("Teleport")
                .displayName("&#4CC9F0ᴛᴘᴀʜᴇʀᴇ ʀᴇǫᴜᴇsᴛs")
                .description(List.of("&#FFFFFFAllow players to pull you to them.", "&#7A7A7AControls incoming /tpahere requests."))
                .material(Material.CLOCK)
                .priority(11)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).acceptsTpaHere())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA_HERE, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.auto")
                .category("Teleport")
                .displayName("&#FFD447ᴛᴘᴀᴜᴛᴏ")
                .description(List.of("&#FFFFFFAutomatically accept TPA requests.", "&#D90429Use carefully near unknown players."))
                .material(Material.REDSTONE_TORCH)
                .priority(12)
                .getter(player -> api.getTeleportModule().getSettingsService().get(player.getUniqueId()).autoAccept())
                .setter((player, enabled) -> api.getTeleportModule().getSettingsService().set(player, TeleportPreference.TPA_AUTO, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("teleport.confirm-menu")
                .category("Teleport")
                .displayName("&#FF2B2Bᴛᴘᴀ ᴄᴏɴꜰɪʀᴍ ᴍᴇɴᴜ")
                .description(List.of("&#FFFFFFShow a menu before sending/accepting.", "&#7A7A7ARecommended for safer teleports."))
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
                .displayName("&#4CC9F0ᴘᴜʙʟɪᴄ ᴄʜᴀᴛ")
                .description(List.of("&#FFFFFFShow or hide member chat.", "&#7A7A7AYou can still send messages."))
                .material(Material.OAK_SIGN)
                .priority(20)
                .getter(player -> api.getChatModule().getSettingsService().canReceiveChat(player.getUniqueId()))
                .setter((player, enabled) -> api.getChatModule().getSettingsService().setChat(player, enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("chat.broadcast")
                .category("Chat")
                .displayName("&#FFD447ʙʀᴏᴀᴅᴄᴀsᴛs")
                .description(List.of("&#FFFFFFShow or hide auto messages.", "&#7A7A7ARaven broadcasts still run on Redis."))
                .material(Material.BELL)
                .priority(21)
                .getter(player -> api.getChatModule().getSettingsService().canReceiveBroadcast(player.getUniqueId()))
                .setter((player, enabled) -> api.getChatModule().getSettingsService().setBroadcast(player, enabled))
                .build());
    }

    private void registerScoreboardSettings() {
        if (api.getScoreboardModule() == null || api.getScoreboardModule().getService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("scoreboard.enabled")
                .category("Visual")
                .displayName("&#3A86FFsᴄᴏʀᴇʙᴏᴀʀᴅ")
                .description(List.of("&#FFFFFFShow or hide your sidebar.", "&#7A7A7ASaved per player."))
                .material(Material.NAME_TAG)
                .priority(30)
                .getter(player -> api.getScoreboardModule().getService().isEnabled(player))
                .setter((player, enabled) -> api.getScoreboardModule().getService().setEnabled(player, enabled))
                .build());
    }

    private void registerEconomySettings() {
        if (api.getEconomyModule() == null || api.getEconomyModule().getSettingsService() == null) {
            return;
        }

        registry.register(PlayerSettingDefinition.builder("economy.payments")
                .category("Economy")
                .displayName("&#33FF66ᴘᴀʏᴍᴇɴᴛs")
                .description(List.of("&#FFFFFFAllow players to send you money.", "&#7A7A7AAdmins with bypass can still pay you."))
                .material(Material.EMERALD)
                .priority(40)
                .getter(player -> api.getEconomyModule().getSettingsService().acceptsPayments(player.getUniqueId()))
                .setter((player, enabled) -> api.getEconomyModule().getSettingsService().setPayments(player.getUniqueId(), enabled))
                .build());

        registry.register(PlayerSettingDefinition.builder("economy.messages")
                .category("Economy")
                .displayName("&#FFD447ᴘᴀʏ ᴍᴇssᴀɢᴇs")
                .description(List.of("&#FFFFFFShow pay and shop economy messages.", "&#7A7A7ATransactions still log normally."))
                .material(Material.PAPER)
                .priority(41)
                .getter(player -> api.getEconomyModule().getSettingsService().paymentMessages(player.getUniqueId()))
                .setter((player, enabled) -> api.getEconomyModule().getSettingsService().setMessages(player.getUniqueId(), enabled))
                .build());
    }
}
