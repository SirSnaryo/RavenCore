package com.kammoun.raven.chat;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.chat.config.ChatMessages;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.redis.event.ChatMessageEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.io.File;
import java.util.List;

public class ChatModule extends KModule<RavenCore> {

    private String format;
    private String serverTag;
    private String permission;
    private ChatMessages messages;

    public ChatModule() {
        super("chat");
    }

    @Override
    public void onModuleEnable() {
        format = getConfig().getString("format", "&#7A7A7A[&#4CC9F0%server%&#7A7A7A] %luckperms_prefix%&#FFFFFF%player%&#7A7A7A: &#FFFFFF%message%");
        String configuredTag = getConfig().getString("server-tag", "");
        serverTag = configuredTag.isEmpty() ? api.getServerId() : configuredTag;
        messages = new ChatMessages(loadMessages());

        onEvent(AsyncPlayerChatEvent.class, EventPriority.HIGH, event -> {
            Player player = event.getPlayer();
            event.setCancelled(true);


            String line = renderLine(player, event.getMessage());

            ChatMessageEvent chatEvent = new ChatMessageEvent();
            chatEvent.setSenderUuid(player.getUniqueId().toString());
            chatEvent.setFormattedMessage(line);
            api.getNetworkEventBus().publish(chatEvent);
        });

        api.getNetworkEventBus().subscribe(ChatMessageEvent.class, event ->
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(event.getFormattedMessage()))
        );

        onEvent(TabCompleteEvent.class, event -> {
            if (event.isCommand() || !(event.getSender() instanceof Player sender)) return;

            String token = lastToken(event.getBuffer());
            List<String> completions = api.getNetworkPlayerManager()
                    .completePlayerNames(token, sender.getName());
            if (!completions.isEmpty()) {
                event.setCompletions(completions);
            }
        });

        getLogger().info("[Chat] Enabled — server tag: " + serverTag);
    }

    @Override
    public void onModuleDisable() {
        api.getNetworkEventBus().unsubscribe(ChatMessageEvent.class);
        getLogger().info("[Chat] Disabled.");
    }

    private String renderLine(Player player, String message) {
        String resolved = format.replace("%server%", serverTag);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }
        resolved = resolved.replace("%player%", player.getName()).replace("%message%", message);
        return ChatFormatter.color(resolved);
    }

    private String lastToken(String buffer) {
        if (buffer == null || buffer.isBlank()) return "";
        int lastSpace = buffer.lastIndexOf(' ');
        return lastSpace == -1 ? buffer : buffer.substring(lastSpace + 1);
    }

    private FileConfiguration loadMessages() {
        saveDefaultConfig("messages.yml");
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
