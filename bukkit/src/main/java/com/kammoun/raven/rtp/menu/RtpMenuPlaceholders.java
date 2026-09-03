package com.kammoun.raven.rtp.menu;

import com.kammoun.raven.library.util.PlaceholderHelper;
import com.kammoun.raven.library.util.NumberUtils;
import com.kammoun.raven.rtp.RtpModule;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class RtpMenuPlaceholders {

    private static final String ONLINE_LINES = "%server_online_lines%";

    private RtpMenuPlaceholders() {}

    static ItemStack apply(ItemStack item, RtpModule.RtpOnlineSnapshot online,
                           Collection<String> serverIds, String... placeholders) {
        List<String> pairs = new ArrayList<>();
        if (placeholders != null) {
            pairs.addAll(List.of(placeholders));
        }
        pairs.add("%online%");
        pairs.add(NumberUtils.compact(online.online(serverIds)));

        ItemStack parsed = PlaceholderHelper.parsePlaceholders(item, pairs.toArray(String[]::new));
        if (parsed == null || !parsed.hasItemMeta()) {
            return parsed;
        }

        ItemMeta meta = parsed.getItemMeta();
        if (!meta.hasLore()) {
            return parsed;
        }

        List<String> lore = new ArrayList<>();
        for (String line : meta.getLore()) {
            if (!line.contains(ONLINE_LINES)) {
                lore.add(line);
            }
        }
        meta.setLore(lore);
        parsed.setItemMeta(meta);
        return parsed;
    }
}
