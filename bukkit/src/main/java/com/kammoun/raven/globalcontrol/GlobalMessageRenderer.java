package com.kammoun.raven.globalcontrol;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GlobalMessageRenderer {

    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)[&\u00A7]#([0-9a-f]{6})");
    private static final Pattern LEGACY_SECTION_HEX = Pattern.compile(
            "(?i)[&\u00A7]x[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])" +
                    "[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])[&\u00A7]([0-9a-f])"
    );
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&\u00A7]([0-9a-fk-or])");
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

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component render(String template, Map<String, String> placeholders) {
        String resolved = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace(entry.getKey(), escape(entry.getValue()));
        }
        return miniMessage.deserialize(legacyToMini(resolved));
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
