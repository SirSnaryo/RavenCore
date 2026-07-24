package com.kammoun.raven.chat.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.chat.config.ChatConfig;
import com.kammoun.raven.chat.model.ChatFormat;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFormatService {

    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)[&\u00A7]#([0-9a-f]{6})");
    private static final Pattern LEGACY_SECTION_HEX = Pattern.compile(
            "(?i)[&\u00A7]x[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])" +
                    "[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])"
    );
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&\u00A7]([0-9a-fk-or])");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://)?[-a-z0-9@:%._+~#=]{1,256}\\.[a-z0-9()]{1,8}\\b[-a-z0-9()@:%_+.~#?&/=]*)"
    );
    private static final Pattern ANY_MENTION = Pattern.compile("(?i)(?<![A-Za-z0-9_])@[A-Za-z0-9_]{3,16}(?![A-Za-z0-9_])");
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?i)(?<![A-Za-z0-9_])@%s(?![A-Za-z0-9_])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private final RavenCore plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySection = LegacyComponentSerializer.legacySection();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    public ChatFormatService(RavenCore plugin) {
        this.plugin = plugin;
    }

    public Component render(Player player, Component signedMessage, String rawMessage, ChatConfig config, String serverTag) {
        String template = applyPlaceholders(player, selectFormat(player, config), serverTag);
        TagResolver messageResolver = TagResolver.resolver(
                "message",
                Tag.inserting(messageComponent(player, signedMessage, rawMessage, config))
        );
        return miniMessage.deserialize(template, messageResolver);
    }

    public Component deserializeConfigured(String text) {
        return miniMessage.deserialize(legacyToMini(text == null ? "" : text));
    }

    public Component deserializeSerialized(String text) {
        return miniMessage.deserialize(text == null ? "" : text);
    }

    public String serialize(Component component) {
        return miniMessage.serialize(component);
    }

    public String plain(Component component) {
        return plainText.serialize(component);
    }

    public boolean mentions(Player receiver, String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile(String.format(MENTION_PATTERN.pattern(), Pattern.quote(receiver.getName())),
                Pattern.CASE_INSENSITIVE);
        return pattern.matcher(rawMessage).find();
    }

    private String selectFormat(Player player, ChatConfig config) {
        for (ChatFormat format : config.chatFormats()) {
            if (format.permission().isBlank() || player.hasPermission(format.permission())) {
                return format.format();
            }
        }
        return config.defaultChatFormat();
    }

    private String applyPlaceholders(Player player, String template, String serverTag) {
        String resolved = template
                .replace("%player%", escape(player.getName()))
                .replace("%displayName%", escape(plainText.serialize(player.displayName())))
                .replace("%moderator_action%", "")
                .replace("%server%", escape(serverTag == null ? "" : serverTag));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
        }
        return legacyToMini(resolved);
    }

    private Component messageComponent(Player player, Component signedMessage, String rawMessage, ChatConfig config) {
        if (rawMessage == null) {
            return signedMessage;
        }
        if (!config.linksEnabled() || !config.linkClickEnabled() || !player.hasPermission("raven.chat.link")) {
            return mentionComponent(player, rawMessage, config);
        }

        Matcher matcher = URL_PATTERN.matcher(rawMessage);
        if (!matcher.find()) {
            return mentionComponent(player, rawMessage, config);
        }

        Component result = Component.empty();
        int cursor = 0;
        matcher.reset();
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result = result.append(mentionComponent(player, rawMessage.substring(cursor, matcher.start()), config));
            }
            String url = matcher.group(1);
            result = result.append(linkComponent(url, config));
            cursor = matcher.end();
        }
        if (cursor < rawMessage.length()) {
            result = result.append(mentionComponent(player, rawMessage.substring(cursor), config));
        }
        return result;
    }

    private Component mentionComponent(Player player, String text, ChatConfig config) {
        Matcher matcher = ANY_MENTION.matcher(text);
        if (!matcher.find()) {
            return coloredMessage(player, text);
        }

        Component result = Component.empty();
        int cursor = 0;
        matcher.reset();
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result = result.append(coloredMessage(player, text.substring(cursor, matcher.start())));
            }
            String mention = matcher.group();
            String format = mention.equalsIgnoreCase("@" + player.getName())
                    ? config.mentionSelfFormat()
                    : config.mentionOtherFormat();
            result = result.append(deserializeConfigured(format.replace("%player%", escape(mention))));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            result = result.append(coloredMessage(player, text.substring(cursor)));
        }
        return result;
    }

    private Component coloredMessage(Player player, String rawMessage) {
        if (!player.hasPermission("raven.chat.color")) {
            return Component.text(rawMessage);
        }
        return legacySection.deserialize(ChatFormatter.color(rawMessage));
    }

    private Component linkComponent(String url, ChatConfig config) {
        String text = config.linkFormat().replace("%url%", escape(url));
        return deserializeConfigured(text).clickEvent(ClickEvent.openUrl(normalizeUrl(url)));
    }

    private String normalizeUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? url : "https://" + url;
    }

    private String legacyToMini(String text) {
        String converted = replaceHex(text);
        Matcher matcher = LEGACY_CODE.matcher(converted);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement = LEGACY_TAGS.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String replaceHex(String text) {
        Matcher sectionMatcher = LEGACY_SECTION_HEX.matcher(text);
        StringBuffer sectionOut = new StringBuffer();
        while (sectionMatcher.find()) {
            String hex = sectionMatcher.group(1) + sectionMatcher.group(2) + sectionMatcher.group(3)
                    + sectionMatcher.group(4) + sectionMatcher.group(5) + sectionMatcher.group(6);
            sectionMatcher.appendReplacement(sectionOut, Matcher.quoteReplacement("<#" + hex + ">"));
        }
        sectionMatcher.appendTail(sectionOut);

        Matcher matcher = LEGACY_HEX.matcher(sectionOut.toString());
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement("<#" + matcher.group(1) + ">"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String escape(String value) {
        return miniMessage.escapeTags(value == null ? "" : value);
    }
}
