package com.kammoun.raven.mythictools.item;

import com.kammoun.raven.library.util.NBTHelper;
import com.kammoun.raven.mythictools.model.MythicToolType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class MythicItemData {

    public static final String TYPE = "mythic_tool_type";
    public static final String EXPIRY = "mythic_expiry_time";
    public static final String DELAYED = "mythic_delayed_start";
    public static final String STARTED = "mythic_timer_started";
    public static final String INSTANCE_ID = "mythic_instance_id";

    private MythicItemData() {}

    public static MythicToolType type(ItemStack item) {
        if (isEmpty(item) || !NBTHelper.hasTag(item, TYPE)) return null;
        try {
            return MythicToolType.valueOf(NBTHelper.getString(item, TYPE));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String instanceId(ItemStack item) {
        return isEmpty(item) || !NBTHelper.hasTag(item, INSTANCE_ID)
                ? null : NBTHelper.getString(item, INSTANCE_ID);
    }

    public static ItemStack initialize(ItemStack item, MythicToolType type, long durationMillis, boolean delayed) {
        ItemStack result = NBTHelper.setString(item, TYPE, type.name());
        result = NBTHelper.setString(result, INSTANCE_ID, UUID.randomUUID().toString());
        result = NBTHelper.setBoolean(result, DELAYED, delayed);
        result = NBTHelper.setBoolean(result, STARTED, !delayed);
        long expiry = delayed ? durationMillis : System.currentTimeMillis() + durationMillis;
        return NBTHelper.setLong(result, EXPIRY, expiry);
    }

    public static boolean timerStarted(ItemStack item) {
        return !NBTHelper.hasTag(item, DELAYED)
                || !NBTHelper.getBoolean(item, DELAYED)
                || NBTHelper.getBoolean(item, STARTED);
    }

    public static boolean timed(ItemStack item) {
        return !isEmpty(item) && NBTHelper.hasTag(item, EXPIRY)
                && NBTHelper.hasTag(item, INSTANCE_ID);
    }

    public static long expiry(ItemStack item) {
        return NBTHelper.hasTag(item, EXPIRY) ? NBTHelper.getLong(item, EXPIRY) : 0L;
    }

    public static ItemStack startTimer(ItemStack item, long now) {
        if (timerStarted(item)) return item;
        long duration = expiry(item);
        ItemStack result = NBTHelper.setLong(item, EXPIRY, now + duration);
        return NBTHelper.setBoolean(result, STARTED, true);
    }

    public static boolean expired(ItemStack item, long now) {
        return timed(item) && timerStarted(item) && expiry(item) > 0L && now >= expiry(item);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
