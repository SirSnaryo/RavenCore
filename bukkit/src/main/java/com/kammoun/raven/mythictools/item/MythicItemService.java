package com.kammoun.raven.mythictools.item;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.mythictools.MythicToolsModule;
import com.kammoun.raven.mythictools.model.MythicToolDefinition;
import com.kammoun.raven.mythictools.model.MythicToolType;
import com.kammoun.raven.mythictools.util.DurationParser;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MythicItemService {

    private static final String TIMER_MARKER = "§0§0mythic-timer";
    private final MythicToolsModule module;

    public MythicItemService(MythicToolsModule module) {
        this.module = module;
    }

    public ItemStack create(MythicToolDefinition definition, boolean delayed) {
        ItemStack item = definition.template().clone();
        item = com.kammoun.raven.library.util.NBTHelper.setString(
                item, MythicItemData.TYPE, definition.type().name());
        if (definition.durationMillis() > 0L) {
            item = MythicItemData.initialize(item, definition.type(), definition.durationMillis(), delayed);
            item = refreshLore(item, System.currentTimeMillis());
        }
        return item;
    }

    public ItemStack applyTimer(ItemStack source, long durationMillis, boolean delayed) {
        ItemStack item = source.clone();
        item = com.kammoun.raven.library.util.NBTHelper.setString(item, MythicItemData.INSTANCE_ID,
                java.util.UUID.randomUUID().toString());
        item = com.kammoun.raven.library.util.NBTHelper.setBoolean(item, MythicItemData.DELAYED, delayed);
        item = com.kammoun.raven.library.util.NBTHelper.setBoolean(item, MythicItemData.STARTED, !delayed);
        item = com.kammoun.raven.library.util.NBTHelper.setLong(item, MythicItemData.EXPIRY,
                delayed ? durationMillis : System.currentTimeMillis() + durationMillis);
        return refreshLore(item, System.currentTimeMillis());
    }

    public ItemStack startDelayedTimer(ItemStack item, long now) {
        if (MythicItemData.timerStarted(item)) return item;
        return refreshLore(MythicItemData.startTimer(item, now), now);
    }

    public ItemStack refreshLore(ItemStack source, long now) {
        if (!source.hasItemMeta()) return source;
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        int marker = lore.indexOf(TIMER_MARKER);
        if (marker >= 0) {
            while (lore.size() > marker) lore.remove(lore.size() - 1);
        }
        lore.add(TIMER_MARKER);
        if (!MythicItemData.timerStarted(item)) {
            lore.add(ChatFormatter.color(module.config().timerDelayed()));
            lore.add(ChatFormatter.color(module.config().timerDuration()
                    .replace("%time%", DurationParser.format(MythicItemData.expiry(item)))));
        } else {
            lore.add(ChatFormatter.color(module.config().timerExpires()
                    .replace("%time%", DurationParser.format(MythicItemData.expiry(item) - now))));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
