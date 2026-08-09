package com.kammoun.raven.mythictools.config;

import com.kammoun.raven.mythictools.MythicToolsModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// saveDefaultConfig() never touches an existing file, so new Multi Tool keys are patched in by hand
// to keep the user's comments and ordering intact.
final class MythicToolsMigrations {

    private static final String MULTI_TOOL_SECTION = "  multi_tool:";
    private static final String LEGACY_LORE = "- \"&#7A7A7AIncludes all Mythic Tool abilities.\"";

    private static final List<String> SWITCH_KEYS = List.of(
            "    switch:",
            "      pickaxe: NETHERITE_PICKAXE",
            "      shovel: NETHERITE_SHOVEL",
            "      axe: NETHERITE_AXE",
            "      sword: NETHERITE_SWORD");

    private static final List<String> OVERRIDE_KEYS = List.of(
            "",
            "# Blocks that force the Multi Tool into a specific tool, overriding vanilla mineable tags.",
            "multi-tool:",
            "  sword:",
            "    - COBWEB",
            "    - BAMBOO",
            "    - BAMBOO_SAPLING",
            "  axe: [ ]",
            "  shovel: [ ]",
            "  pickaxe: [ ]");

    private MythicToolsMigrations() {}

    static void run(MythicToolsModule module, File tools, File blocks) {
        migrateTools(module, tools);
        migrateBlocks(module, blocks);
    }

    private static void migrateTools(MythicToolsModule module, File file) {
        List<String> lines = read(module, file);
        if (lines == null) return;
        int start = lines.indexOf(MULTI_TOOL_SECTION);
        if (start < 0) return;
        int end = start + 1;
        while (end < lines.size() && (lines.get(end).isBlank() || lines.get(end).startsWith("    "))) end++;

        boolean changed = false;
        for (int i = end - 1; i > start; i--) {
            if (lines.get(i).trim().equals(LEGACY_LORE)) {
                lines.remove(i);
                end--;
                changed = true;
            }
        }

        int material = -1;
        boolean hasSwitch = false;
        for (int i = start + 1; i < end; i++) {
            if (lines.get(i).startsWith("    switch:")) hasSwitch = true;
            if (lines.get(i).startsWith("    material:")) material = i;
        }
        if (!hasSwitch) {
            lines.addAll(material >= 0 ? material + 1 : start + 1, SWITCH_KEYS);
            changed = true;
        }
        if (changed) write(module, file, lines);
    }

    private static void migrateBlocks(MythicToolsModule module, File file) {
        List<String> lines = read(module, file);
        if (lines == null || lines.stream().anyMatch(line -> line.startsWith("multi-tool:"))) return;
        lines.addAll(OVERRIDE_KEYS);
        write(module, file, lines);
    }

    private static List<String> read(MythicToolsModule module, File file) {
        if (!file.isFile()) return null;
        try {
            return new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            module.getLogger().warning("[MythicTools] Could not read " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static void write(MythicToolsModule module, File file, List<String> lines) {
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            module.getLogger().info("[MythicTools] Migrated " + file.getName() + " with Multi Tool keys.");
        } catch (IOException e) {
            module.getLogger().warning("[MythicTools] Could not migrate " + file.getName() + ": " + e.getMessage());
        }
    }
}
