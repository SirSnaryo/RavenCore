package com.kammoun.raven.library.platform.chat;

import org.bukkit.ChatColor;

public class ChatFormatter {

    private static final HexColorCodes HEX_COLOR_CODES = new HexColorCodes();

    public static HexColorCodes getHexColorCodes() {
        return HEX_COLOR_CODES;
    }

    public static String color(String toColor){
        if(toColor == null || toColor.isEmpty())return toColor;
        return ChatColor.translateAlternateColorCodes('&', HEX_COLOR_CODES.FormatHex(toColor));
    }

    public static String strip(String text) {
        if (text == null || text.isEmpty()) return text;
        return ChatColor.stripColor(color(text));
    }

    public static String menuTitle(String title) {
        String plainTitle = strip(title);
        return color("&8" + (plainTitle == null ? "" : plainTitle));
    }

}
