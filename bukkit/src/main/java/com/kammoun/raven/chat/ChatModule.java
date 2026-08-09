package com.kammoun.raven.chat;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.chat.command.BroadcastToggleCommand;
import com.kammoun.raven.chat.command.ChatToggleCommand;
import com.kammoun.raven.chat.config.ChatConfig;
import com.kammoun.raven.chat.config.ChatMessages;
import com.kammoun.raven.chat.service.ChatAutoMessageService;
import com.kammoun.raven.chat.service.ChatFormatService;
import com.kammoun.raven.chat.service.ChatGuard;
import com.kammoun.raven.chat.settings.ChatSettingsService;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.redis.event.ChatBroadcastEvent;
import com.kammoun.raven.library.redis.event.ChatMessageEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ChatModule extends KModule<RavenCore> {

    private final ChatGuard guard = new ChatGuard();
    private final Map<String, KSound> sounds = new HashMap<>();

    private ChatMessages messages;
    private ChatSettingsService settingsService;
    private ChatFormatService formatter;
    private ChatAutoMessageService autoMessages;
    private ChatConfig chatConfig;

    public ChatModule() {
        super("chat");
    }

    @Override
    public void onModuleEnable() {
        reloadConfig();
        messages = new ChatMessages(loadMessages());
        settingsService = new ChatSettingsService(api, getLogger());
        formatter = new ChatFormatService(api);
        autoMessages = new ChatAutoMessageService(
                api.getNetworkEventBus(),
                api.getRedisMessenger().getConnection().getPool(),
                api.getServerId(),
                formatter,
                getLogger()
        );
        loadRuntimeConfig();

        Bukkit.getOnlinePlayers().forEach(settingsService::load);

        registerCommand(new ChatToggleCommand(this), "chattoggle");
        registerCommand(new BroadcastToggleCommand(this), "broadcasttoggle");

        onEvent(AsyncChatEvent.class, EventPriority.HIGH, this::onChat);
        onEvent(PlayerJoinEvent.class, event -> settingsService.load(event.getPlayer()));
        onEvent(PlayerQuitEvent.class, event -> {
            settingsService.unload(event.getPlayer());
            guard.forget(event.getPlayer());
        });
        onEvent(TabCompleteEvent.class, this::onTabComplete);

        api.getNetworkEventBus().subscribe(ChatMessageEvent.class, this::deliverChat);
        api.getNetworkEventBus().subscribe(ChatBroadcastEvent.class, this::deliverBroadcast);

        autoMessages.start(chatConfig);
        getLogger().info("[Chat] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (autoMessages != null) {
            autoMessages.stop();
        }
        api.getNetworkEventBus().unsubscribe(ChatMessageEvent.class);
        api.getNetworkEventBus().unsubscribe(ChatBroadcastEvent.class);
        guard.clear();
        getLogger().info("[Chat] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        messages = new ChatMessages(loadMessages());
        loadRuntimeConfig();
        autoMessages.start(chatConfig);
    }

    public ChatSettingsService getSettingsService() {
        return settingsService;
    }

    public void sendToggleState(Player player, String key, boolean enabled) {
        String messageKey = enabled ? key + "-toggle-on" : key + "-toggle-off";
        messages.send(player, messageKey);
        actionBar(player, messages.get(messageKey + "-actionbar"));
        sound(player, "toggle");
    }

    private void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!canSend(player)) {
            messages.send(player, "no-permission");
            sound(player, "error");
            return;
        }

        String rawMessage = formatter.plain(event.message());
        String rejection = guard.validate(player, rawMessage, chatConfig);
        if (rejection != null) {
            messages.send(player, rejection);
            sound(player, "error");
            return;
        }

        guard.record(player, rawMessage);
        Component formatted = formatter.render(player, event.message(), rawMessage, chatConfig, serverTag());

        ChatMessageEvent chatEvent = new ChatMessageEvent();
        chatEvent.setSenderUuid(player.getUniqueId().toString());
        chatEvent.setSenderName(player.getName());
        chatEvent.setSenderServer(serverTag());
        chatEvent.setRawMessage(rawMessage);
        chatEvent.setFormattedMessage(formatter.serialize(formatted));
        api.getNetworkEventBus().publish(chatEvent);
    }

    private void deliverChat(ChatMessageEvent event) {
        UUID senderUuid = parseUuid(event.getSenderUuid());
        Component message = formatter.deserializeSerialized(event.getFormattedMessage());
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (!shouldReceiveChat(receiver, senderUuid)) {
                continue;
            }
            SchedulerUtil.execute(receiver, () -> {
                receiver.sendMessage(message);
                if (formatter.mentions(receiver, event.getRawMessage())) {
                    sound(receiver, "mention");
                }
            });
        }
    }

    private void deliverBroadcast(ChatBroadcastEvent event) {
        Component message = formatter.deserializeConfigured(event.getMessage());
        Component actionBar = formatter.deserializeConfigured(event.getActionBar());
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (!settingsService.canReceiveBroadcast(receiver.getUniqueId())) {
                continue;
            }
            SchedulerUtil.execute(receiver, () -> {
                if (event.getMessage() != null && !event.getMessage().isBlank()) {
                    receiver.sendMessage(message);
                }
                if (event.getActionBar() != null && !event.getActionBar().isBlank()) {
                    receiver.sendActionBar(actionBar);
                }
                sound(receiver, event.getSoundKey());
            });
        }
    }

    private void onTabComplete(TabCompleteEvent event) {
        if (event.isCommand() || !(event.getSender() instanceof Player sender)) {
            return;
        }

        List<String> completions = api.getNetworkPlayerManager()
                .completePlayerNames(lastToken(event.getBuffer()), sender.getName());
        if (!completions.isEmpty()) {
            event.setCompletions(completions);
        }
    }

    private boolean canSend(Player player) {
        String permission = chatConfig.permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private boolean shouldReceiveChat(Player receiver, UUID senderUuid) {
        if (senderUuid != null && receiver.getUniqueId().equals(senderUuid)) {
            return true;
        }
        String bypassPermission = chatConfig.toggleBypassPermission();
        if (bypassPermission != null && !bypassPermission.isBlank() && receiver.hasPermission(bypassPermission)) {
            return true;
        }
        return settingsService.canReceiveChat(receiver.getUniqueId());
    }

    private void loadRuntimeConfig() {
        chatConfig = ChatConfig.from(getConfig(), api.getServerDisplayName());
        sounds.clear();

        ConfigurationSection soundSection = getConfig().getConfigurationSection("sounds");
        if (soundSection == null) {
            return;
        }
        for (String key : soundSection.getKeys(false)) {
            KSound sound = KSound.getSoundFromConfig(soundSection.getConfigurationSection(key));
            if (sound != null) {
                sounds.put(key.toLowerCase(Locale.ROOT), sound);
            }
        }
    }

    private String serverTag() {
        return chatConfig.resolvedServerTag(api.getServerDisplayName());
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    private String lastToken(String buffer) {
        if (buffer == null || buffer.isBlank()) {
            return "";
        }
        int lastSpace = buffer.lastIndexOf(' ');
        return lastSpace == -1 ? buffer : buffer.substring(lastSpace + 1);
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void sound(Player player, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        KSound sound = sounds.get(key.toLowerCase(Locale.ROOT));
        if (sound != null) {
            sound.playSound(player);
        }
    }

    private void actionBar(Player player, String text) {
        if (text != null && !text.isBlank()) {
            player.sendActionBar(formatter.deserializeConfigured(text));
        }
    }
}
