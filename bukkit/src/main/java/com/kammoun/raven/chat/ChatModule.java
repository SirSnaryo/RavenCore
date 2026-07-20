package com.kammoun.raven.chat;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.chat.command.BroadcastToggleCommand;
import com.kammoun.raven.chat.command.ChatToggleCommand;
import com.kammoun.raven.chat.config.ChatMessages;
import com.kammoun.raven.chat.settings.ChatSettingsService;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.library.redis.event.ChatBroadcastEvent;
import com.kammoun.raven.library.redis.event.ChatMessageEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class ChatModule extends KModule<RavenCore> {

    private static final String AUTO_MESSAGE_INDEX_KEY = "raven:chat:auto:index";

    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<String, KSound> sounds = new HashMap<>();

    private String format;
    private String serverTag;
    private String permission;
    private boolean linksEnabled;
    private boolean sameMessageCancel;
    private boolean capsEnabled;
    private boolean floodEnabled;
    private boolean autoMessagesEnabled;
    private boolean richChatEnabled;
    private boolean senderHoverEnabled;
    private boolean senderClickEnabled;
    private boolean messageHoverEnabled;
    private boolean linkClickEnabled;
    private int maxLength;
    private long cooldownMillis;
    private double capsThreshold;
    private Pattern floodPattern;
    private List<Pattern> blockedPatterns = List.of();
    private String mentionSelfFormat;
    private String mentionOtherFormat;
    private String linkFormat;
    private String senderClickAction;
    private String senderClickValue;
    private List<String> senderHoverLines = List.of();
    private List<String> messageHoverLines = List.of();
    private List<ChatFormat> chatFormats = List.of();
    private ChatMessages messages;
    private ChatSettingsService settingsService;
    private SchedulerUtil.Task autoMessageTask;
    private List<AutoMessage> autoMessages = List.of();
    private String autoMessageLockKey;
    private int autoMessageLockTtlSeconds;

    public ChatModule() {
        super("chat");
    }

    @Override
    public void onModuleEnable() {
        messages = new ChatMessages(loadMessages());
        settingsService = new ChatSettingsService(api, getLogger());
        loadRuntimeConfig();

        Bukkit.getOnlinePlayers().forEach(settingsService::load);

        registerCommand(new ChatToggleCommand(this), "chattoggle");
        registerCommand(new BroadcastToggleCommand(this), "broadcasttoggle");

        onEvent(AsyncPlayerChatEvent.class, EventPriority.HIGH, this::onChat);
        onEvent(PlayerJoinEvent.class, event -> settingsService.load(event.getPlayer()));
        onEvent(PlayerQuitEvent.class, event -> {
            settingsService.unload(event.getPlayer());
            lastChatAt.remove(event.getPlayer().getUniqueId());
            lastMessages.remove(event.getPlayer().getUniqueId());
        });

        api.getNetworkEventBus().subscribe(ChatMessageEvent.class, this::deliverChat);
        api.getNetworkEventBus().subscribe(ChatBroadcastEvent.class, this::deliverBroadcast);

        onEvent(TabCompleteEvent.class, event -> {
            if (event.isCommand() || !(event.getSender() instanceof Player sender)) return;

            String token = lastToken(event.getBuffer());
            List<String> completions = api.getNetworkPlayerManager()
                    .completePlayerNames(token, sender.getName());
            if (!completions.isEmpty()) {
                event.setCompletions(completions);
            }
        });

        startAutoMessages();
        getLogger().info("[Chat] Enabled - server tag: " + serverTag);
    }

    @Override
    public void onModuleDisable() {
        if (autoMessageTask != null) {
            autoMessageTask.cancel();
            autoMessageTask = null;
        }
        api.getNetworkEventBus().unsubscribe(ChatMessageEvent.class);
        api.getNetworkEventBus().unsubscribe(ChatBroadcastEvent.class);
        lastChatAt.clear();
        lastMessages.clear();
        getLogger().info("[Chat] Disabled.");
    }

    @Override
    public void reload() {
        reloadConfig();
        messages = new ChatMessages(loadMessages());
        loadRuntimeConfig();
        if (autoMessageTask != null) {
            autoMessageTask.cancel();
            autoMessageTask = null;
        }
        startAutoMessages();
    }

    public void sendToggleState(Player player, String key, boolean enabled) {
        String messageKey = enabled ? key + "-toggle-on" : key + "-toggle-off";
        String actionKey = messageKey + "-actionbar";
        messages.send(player, messageKey);
        actionBar(player, messages.get(actionKey));
        sound(player, "toggle");
    }

    private void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            messages.send(player, "no-permission");
            sound(player, "error");
            return;
        }

        String rawMessage = event.getMessage();
        String rejection = validate(player, rawMessage);
        if (rejection != null) {
            messages.send(player, rejection);
            sound(player, "error");
            return;
        }

        lastChatAt.put(player.getUniqueId(), System.currentTimeMillis());
        lastMessages.put(player.getUniqueId(), rawMessage);

        ChatMessageEvent chatEvent = new ChatMessageEvent();
        chatEvent.setSenderUuid(player.getUniqueId().toString());
        chatEvent.setSenderName(player.getName());
        chatEvent.setSenderServer(serverTag);
        chatEvent.setRawMessage(rawMessage);
        chatEvent.setFormattedMessage(renderLine(player, rawMessage));
        api.getNetworkEventBus().publish(chatEvent);
    }

    private void deliverChat(ChatMessageEvent event) {
        UUID senderUuid = parseUuid(event.getSenderUuid());
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (!shouldReceiveChat(receiver, senderUuid)) {
                continue;
            }
            SchedulerUtil.execute(receiver, () -> {
                receiver.spigot().sendMessage(richComponents(receiver, event));
            });
        }
    }

    private void deliverBroadcast(ChatBroadcastEvent event) {
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            if (!settingsService.canReceiveBroadcast(receiver.getUniqueId())) {
                continue;
            }
            SchedulerUtil.execute(receiver, () -> {
                if (event.getMessage() != null && !event.getMessage().isBlank()) {
                    receiver.sendMessage(event.getMessage());
                }
                if (event.getActionBar() != null && !event.getActionBar().isBlank()) {
                    actionBar(receiver, event.getActionBar());
                }
                sound(receiver, event.getSoundKey());
            });
        }
    }

    private boolean shouldReceiveChat(Player receiver, UUID senderUuid) {
        if (senderUuid != null && receiver.getUniqueId().equals(senderUuid)) {
            return true;
        }
        if (receiver.hasPermission("raven.chat.bypass.toggle")) {
            return true;
        }
        return settingsService.canReceiveChat(receiver.getUniqueId());
    }

    private String validate(Player player, String message) {
        if (maxLength > 0 && message.length() > maxLength && !player.hasPermission("raven.chat.bypass.length")) {
            return "too-long";
        }

        long last = lastChatAt.getOrDefault(player.getUniqueId(), 0L);
        long remaining = cooldownMillis - (System.currentTimeMillis() - last);
        if (cooldownMillis > 0 && remaining > 0 && !player.hasPermission("raven.chat.bypass.cooldown")) {
            return "cooldown";
        }

        String previous = lastMessages.get(player.getUniqueId());
        if (sameMessageCancel && previous != null && previous.equalsIgnoreCase(message)
                && !player.hasPermission("raven.chat.bypass.same")) {
            return "same-message";
        }

        if (capsEnabled && hasTooManyCaps(message) && !player.hasPermission("raven.chat.bypass.caps")) {
            return "caps";
        }

        if (floodEnabled && floodPattern != null && floodPattern.matcher(message).find()
                && !player.hasPermission("raven.chat.bypass.flood")) {
            return "flood";
        }

        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(message).find() && !player.hasPermission("raven.chat.bypass.filter")) {
                return "blocked";
            }
        }

        return null;
    }

    private String renderLine(Player player, String message) {
        String chatMessage = player.hasPermission("raven.chat.color") ? message : message.replace('&', '＆');
        if (linksEnabled && player.hasPermission("raven.chat.link")) {
            chatMessage = colorLinks(chatMessage);
        }

        String resolved = selectFormat(player)
                .replace("%server%", serverTag)
                .replace("%player%", player.getName())
                .replace("%message%", chatMessage);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }
        return ChatFormatter.color(resolved);
    }

    private BaseComponent[] richComponents(Player receiver, ChatMessageEvent event) {
        String line = decorateTextForViewer(receiver, event);
        List<BaseComponent> components = new ArrayList<>(Arrays.asList(TextComponent.fromLegacyText(line)));
        if (!richChatEnabled) {
            return components.toArray(BaseComponent[]::new);
        }

        components = applySenderInteraction(components, receiver, event);
        if (linkClickEnabled) {
            components = applyLinkInteraction(components);
        }
        if (messageHoverEnabled && !messageHoverLines.isEmpty()) {
            HoverEvent hover = hoverEvent(formatLines(messageHoverLines, receiver, event));
            for (BaseComponent component : components) {
                if (component.getHoverEvent() == null) {
                    component.setHoverEvent(hover);
                }
            }
        }
        return components.toArray(BaseComponent[]::new);
    }

    private String decorateTextForViewer(Player receiver, ChatMessageEvent event) {
        String line = event.getFormattedMessage() == null ? "" : event.getFormattedMessage();
        String raw = event.getRawMessage() == null ? "" : event.getRawMessage();
        String receiverMention = "@" + receiver.getName();
        Pattern selfMentionPattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(receiverMention) + "(?![A-Za-z0-9_])");
        if (selfMentionPattern.matcher(raw).find()) {
            sound(receiver, "mention");
            return selfMentionPattern.matcher(line)
                    .replaceAll(Matcher.quoteReplacement(mentionSelfFormat.replace("%player%", receiverMention)));
        }

        Pattern anyMentionPattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])@[A-Za-z0-9_]{3,16}(?![A-Za-z0-9_])");
        return anyMentionPattern.matcher(line).replaceAll(match ->
                Matcher.quoteReplacement(mentionOtherFormat.replace("%player%", match.group())));
    }

    private List<BaseComponent> applySenderInteraction(List<BaseComponent> components, Player receiver, ChatMessageEvent event) {
        String senderName = event.getSenderName();
        if (senderName == null || senderName.isBlank()) {
            return components;
        }

        HoverEvent hover = senderHoverEnabled && !senderHoverLines.isEmpty()
                ? hoverEvent(formatLines(senderHoverLines, receiver, event))
                : null;
        ClickEvent click = senderClickEnabled
                ? clickEvent(senderClickAction, format(senderClickValue, receiver, event))
                : null;

        for (BaseComponent component : components) {
            if (component instanceof TextComponent textComponent
                    && textComponent.getText() != null
                    && textComponent.getText().contains(senderName)) {
                if (hover != null) {
                    component.setHoverEvent(hover);
                }
                if (click != null) {
                    component.setClickEvent(click);
                }
            }
        }
        return components;
    }

    private List<BaseComponent> applyLinkInteraction(List<BaseComponent> components) {
        List<BaseComponent> result = new ArrayList<>();
        Pattern linkPattern = Pattern.compile("(?i)\\b((https?://)?[-a-z0-9@:%._+~#=]{1,256}\\.[a-z0-9()]{1,8}\\b[-a-z0-9()@:%_+.~#?&/=]*)");
        for (BaseComponent component : components) {
            if (!(component instanceof TextComponent textComponent) || textComponent.getText() == null) {
                result.add(component);
                continue;
            }

            String text = textComponent.getText();
            Matcher matcher = linkPattern.matcher(text);
            int lastIndex = 0;
            boolean found = false;
            while (matcher.find()) {
                found = true;
                if (matcher.start() > lastIndex) {
                    result.add(copyText(textComponent, text.substring(lastIndex, matcher.start())));
                }

                String url = matcher.group(1);
                TextComponent link = copyText(textComponent, url);
                link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, normalizeUrl(url)));
                link.setHoverEvent(hoverEvent(ChatFormatter.color("&#4CC9F0Open link: &#FFFFFF" + url)));
                result.add(link);
                lastIndex = matcher.end();
            }

            if (!found) {
                result.add(component);
            } else if (lastIndex < text.length()) {
                result.add(copyText(textComponent, text.substring(lastIndex)));
            }
        }
        return result;
    }

    private TextComponent copyText(TextComponent source, String text) {
        TextComponent copy = new TextComponent(source);
        copy.setText(text);
        return copy;
    }

    private HoverEvent hoverEvent(String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(text));
    }

    private ClickEvent clickEvent(String action, String value) {
        try {
            return new ClickEvent(ClickEvent.Action.valueOf(action.toUpperCase(Locale.ROOT)), value);
        } catch (IllegalArgumentException ignored) {
            return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, value);
        }
    }

    private String normalizeUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? url : "https://" + url;
    }

    private String formatLines(List<String> lines, Player receiver, ChatMessageEvent event) {
        return ChatFormatter.color(String.join("\n", lines.stream()
                .map(line -> format(line, receiver, event))
                .toList()));
    }

    private String format(String text, Player receiver, ChatMessageEvent event) {
        if (text == null) {
            return "";
        }
        return text
                .replace("%player%", event.getSenderName() == null ? "" : event.getSenderName())
                .replace("%server%", event.getSenderServer() == null ? "" : event.getSenderServer())
                .replace("%message%", event.getRawMessage() == null ? "" : event.getRawMessage())
                .replace("%receiver%", receiver.getName());
    }

    private void startAutoMessages() {
        if (!autoMessagesEnabled || autoMessages.isEmpty()) {
            return;
        }

        long interval = Math.max(20L, getConfig().getLong("auto-messages.interval-ticks", 2400L));
        autoMessageTask = SchedulerUtil.runAsyncTimer(this::publishNextAutoMessage, interval, interval);
    }

    private void publishNextAutoMessage() {
        JedisPool pool = api.getRedisMessenger().getConnection().getPool();
        if (pool == null || autoMessages.isEmpty()) {
            return;
        }

        try (Jedis jedis = pool.getResource()) {
            String ok = jedis.set(autoMessageLockKey, api.getServerId(),
                    SetParams.setParams().nx().ex(autoMessageLockTtlSeconds));
            if (!"OK".equals(ok)) {
                return;
            }

            long index = jedis.incr(AUTO_MESSAGE_INDEX_KEY) - 1L;
            AutoMessage autoMessage = autoMessages.get((int) (index % autoMessages.size()));
            ChatBroadcastEvent event = new ChatBroadcastEvent();
            event.setMessage(ChatFormatter.color(autoMessage.message()));
            event.setActionBar(ChatFormatter.color(autoMessage.actionBar()));
            event.setSoundKey(autoMessage.soundKey());
            api.getNetworkEventBus().publish(event);
        } catch (Exception e) {
            getLogger().warning("[Chat] Failed to publish auto message: " + e.getMessage());
        }
    }

    private void loadRuntimeConfig() {
        FileConfiguration config = getConfig();
        format = config.getString("format", "&#7A7A7A[&#4CC9F0%server%&#7A7A7A] %luckperms_prefix%&#FFFFFF%player%&#7A7A7A: &#FFFFFF%message%");
        String configuredTag = config.getString("server-tag", "");
        serverTag = configuredTag == null || configuredTag.isBlank() ? api.getServerDisplayName() : configuredTag;
        permission = config.getString("permission", "raven.chat");

        maxLength = config.getInt("limits.max-length", 256);
        cooldownMillis = Math.max(0L, config.getLong("limits.cooldown-millis", 1000L));
        sameMessageCancel = config.getBoolean("rules.same-message-cancel", true);
        capsEnabled = config.getBoolean("rules.caps.enabled", true);
        capsThreshold = config.getDouble("rules.caps.threshold", 0.75D);
        floodEnabled = config.getBoolean("rules.flood.enabled", true);
        floodPattern = Pattern.compile(config.getString("rules.flood.regex", "(.)\\1{4,}"));
        blockedPatterns = compilePatterns(config.getStringList("rules.blocked-patterns"));

        linksEnabled = config.getBoolean("links.enabled", true);
        linkClickEnabled = config.getBoolean("links.clickable", true);
        linkFormat = config.getString("links.format", "&#4CC9F0%url%");
        mentionSelfFormat = ChatFormatter.color(config.getString("mentions.self-format", "&#FFD447&l%player%"));
        mentionOtherFormat = ChatFormatter.color(config.getString("mentions.other-format", "&#4CC9F0%player%"));
        richChatEnabled = config.getBoolean("rich-chat.enabled", true);
        senderHoverEnabled = config.getBoolean("rich-chat.sender-hover.enabled", true);
        senderHoverLines = config.getStringList("rich-chat.sender-hover.lines");
        if (senderHoverLines.isEmpty()) {
            senderHoverLines = List.of(
                    "&#FF2B2B&l%player%",
                    "&#7A7A7AServer: &#FFFFFF%server%",
                    "&#7A7A7AClick to message this player."
            );
        }
        senderClickEnabled = config.getBoolean("rich-chat.sender-click.enabled", true);
        senderClickAction = config.getString("rich-chat.sender-click.action", "SUGGEST_COMMAND");
        senderClickValue = config.getString("rich-chat.sender-click.value", "/msg %player% ");
        messageHoverEnabled = config.getBoolean("rich-chat.message-hover.enabled", false);
        messageHoverLines = config.getStringList("rich-chat.message-hover.lines");
        if (messageHoverLines.isEmpty()) {
            messageHoverLines = List.of("&#7A7A7AFrom &#FFFFFF%player% &#7A7A7Aon &#FFFFFF%server%");
        }

        sounds.clear();
        ConfigurationSection soundSection = config.getConfigurationSection("sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                KSound sound = KSound.getSoundFromConfig(soundSection.getConfigurationSection(key));
                if (sound != null) {
                    sounds.put(key.toLowerCase(Locale.ROOT), sound);
                }
            }
        }

        autoMessagesEnabled = config.getBoolean("auto-messages.enabled", false);
        autoMessages = loadAutoMessages(config);
        autoMessageLockKey = config.getString("auto-messages.redis-lock-key", "raven:chat:auto:lock");
        autoMessageLockTtlSeconds = Math.max(2, config.getInt("auto-messages.lock-ttl-seconds", 8));
        chatFormats = loadFormats(config);
    }

    private String selectFormat(Player player) {
        for (ChatFormat chatFormat : chatFormats) {
            if (chatFormat.permission().isBlank() || player.hasPermission(chatFormat.permission())) {
                return chatFormat.format();
            }
        }
        return format;
    }

    private List<ChatFormat> loadFormats(FileConfiguration config) {
        List<ChatFormat> formats = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("formats")) {
            String permission = stringValue(map, "permission", "");
            String value = stringValue(map, "format", "");
            if (!value.isBlank()) {
                formats.add(new ChatFormat(permission, value, intValue(map, "priority", 0)));
            }
        }
        formats.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        return List.copyOf(formats);
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            compiled.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
        return List.copyOf(compiled);
    }

    private List<AutoMessage> loadAutoMessages(FileConfiguration config) {
        boolean hasConfiguredMessages = config.isSet("auto-messages.messages");
        List<AutoMessage> messages = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("auto-messages.messages")) {
            String message = stringValue(map, "message", "");
            String actionBar = stringValue(map, "action-bar", "");
            String sound = stringValue(map, "sound", "broadcast");
            if (!message.isBlank() || !actionBar.isBlank()) {
                messages.add(new AutoMessage(message, actionBar, sound));
            }
        }
        if (messages.isEmpty() && !hasConfiguredMessages) {
            messages.add(new AutoMessage(
                    "&#FF2B2B&lRaven SMP &#7A7A7AUse &#FFFFFF/settings &#7A7A7Ato control chat and broadcasts.",
                    "&#4CC9F0Raven &#7A7A7Asettings are synced across the network.",
                    "broadcast"
            ));
            messages.add(new AutoMessage(
                    "&#FFD447&lTip &#7A7A7AMention a player with &#FFFFFF@name &#7A7A7Ato ping them.",
                    "",
                    "broadcast"
            ));
        }
        return List.copyOf(messages);
    }

    private String stringValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean hasTooManyCaps(String message) {
        int upper = 0;
        int letters = 0;
        for (char c : message.toCharArray()) {
            if (!Character.isLetter(c)) continue;
            letters++;
            if (Character.isUpperCase(c)) {
                upper++;
            }
        }
        return letters >= 5 && ((double) upper / letters) > capsThreshold;
    }

    private String colorLinks(String message) {
        Pattern linkPattern = Pattern.compile("(?i)\\b((https?://)?[-a-z0-9@:%._+~#=]{1,256}\\.[a-z0-9()]{1,8}\\b[-a-z0-9()@:%_+.~#?&/=]*)");
        return linkPattern.matcher(message).replaceAll(match ->
                linkFormat.replace("%url%", match.group()));
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
        if (text == null || text.isBlank()) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    private record AutoMessage(String message, String actionBar, String soundKey) {}
    private record ChatFormat(String permission, String format, int priority) {}
}
