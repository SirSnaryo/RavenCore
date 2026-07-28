package com.kammoun.raven.library.util;

import de.tr7zw.changeme.nbtapi.NBTEntity;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * Thin wrapper over NBTAPI for common NBT read/write operations on items and entities.
 */
public final class NBTHelper {

    private NBTHelper() {}

    // ─── Item ───────────────────────────────────────────────────────────────

    public static boolean hasTag(ItemStack item, String key) {
        return new NBTItem(item).hasTag(key);
    }

    public static String getString(ItemStack item, String key) {
        return new NBTItem(item).getString(key);
    }

    public static int getInt(ItemStack item, String key) {
        return new NBTItem(item).getInteger(key);
    }

    public static double getDouble(ItemStack item, String key) {
        return new NBTItem(item).getDouble(key);
    }

    public static boolean getBoolean(ItemStack item, String key) {
        return new NBTItem(item).getBoolean(key);
    }

    public static long getLong(ItemStack item, String key) {
        return new NBTItem(item).getLong(key);
    }

    public static ItemStack setString(ItemStack item, String key, String value) {
        NBTItem nbt = new NBTItem(item);
        nbt.setString(key, value);
        return nbt.getItem();
    }

    public static ItemStack setInt(ItemStack item, String key, int value) {
        NBTItem nbt = new NBTItem(item);
        nbt.setInteger(key, value);
        return nbt.getItem();
    }

    public static ItemStack setDouble(ItemStack item, String key, double value) {
        NBTItem nbt = new NBTItem(item);
        nbt.setDouble(key, value);
        return nbt.getItem();
    }

    public static ItemStack setBoolean(ItemStack item, String key, boolean value) {
        NBTItem nbt = new NBTItem(item);
        nbt.setBoolean(key, value);
        return nbt.getItem();
    }

    public static ItemStack setLong(ItemStack item, String key, long value) {
        NBTItem nbt = new NBTItem(item);
        nbt.setLong(key, value);
        return nbt.getItem();
    }

    public static ItemStack removeTag(ItemStack item, String key) {
        NBTItem nbt = new NBTItem(item);
        nbt.removeKey(key);
        return nbt.getItem();
    }

    // ─── Entity ─────────────────────────────────────────────────────────────

    public static boolean hasTag(Entity entity, String key) {
        return new NBTEntity(entity).hasTag(key);
    }

    public static String getString(Entity entity, String key) {
        return new NBTEntity(entity).getString(key);
    }

    public static int getInt(Entity entity, String key) {
        return new NBTEntity(entity).getInteger(key);
    }

    public static void setString(Entity entity, String key, String value) {
        new NBTEntity(entity).setString(key, value);
    }

    public static void setInt(Entity entity, String key, int value) {
        new NBTEntity(entity).setInteger(key, value);
    }

    public static void removeTag(Entity entity, String key) {
        new NBTEntity(entity).removeKey(key);
    }
}
