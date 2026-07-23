package com.kammoun.raven.library.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PlaceholderHelper {

    public static String parsePlaceholders(String message, String... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return message;
        }
        if (placeholders.length % 2 != 0) {
            return message;
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = placeholders[i];
            String replacement = placeholders[i + 1];

            if (placeholder != null && replacement != null) {
                message = message.replace(placeholder, replacement);
                String key = placeholder;
                if (key.startsWith("%") && key.endsWith("%") && key.length() > 2) {
                    key = key.substring(1, key.length() - 1);
                } else if (key.startsWith("{") && key.endsWith("}") && key.length() > 2) {
                    key = key.substring(1, key.length() - 1);
                }
                message = message.replace("%" + key + "%", replacement)
                        .replace("{" + key + "}", replacement);
            }
        }
        return message;
    }

    public static ItemStack parsePlaceholders(ItemStack itemStack, String... placeholders) {
        if (itemStack == null || placeholders == null || placeholders.length == 0 || placeholders.length % 2 != 0) {
            return itemStack;
        }

        ItemStack newItem = itemStack.clone();
        if (!newItem.hasItemMeta()) {
            return newItem;
        }

        ItemMeta meta = newItem.getItemMeta();

        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            for (int i = 0; i < placeholders.length; i += 2) {
                name = parsePlaceholders(name, placeholders[i], placeholders[i + 1]);
            }
            meta.setDisplayName(name);
        }
        List<String> lore = meta.getLore();
        if (meta.hasLore()) {
            List<String> newLore = new ArrayList<>();
            for (String line : lore) {
                String updatedLine = line;
                for (int i = 0; i < placeholders.length; i += 2) {
                    updatedLine = parsePlaceholders(
                            updatedLine, placeholders[i], placeholders[i + 1]);
                }
                newLore.add(updatedLine);
            }
            meta.setLore(newLore);
        }

        newItem.setItemMeta(meta);
        return newItem;
    }

}
