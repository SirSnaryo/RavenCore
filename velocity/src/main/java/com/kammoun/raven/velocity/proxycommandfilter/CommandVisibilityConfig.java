package com.kammoun.raven.velocity.proxycommandfilter;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CommandVisibilityConfig {
    private static final List<String> DEFAULT_GROUP_ORDER =
            List.of("raven", "supreme", "titan", "mvp", "vip", "custom", "default");
    private static final List<String> DEFAULT_COMMANDS = List.of(
            "discord", "store", "rules", "help", "lobby", "hub", "msg", "w", "tell", "pm", "message",
            "r", "reply", "media", "spawn", "afk", "rtp", "rtpq", "tpa", "tpahere", "tpaccept",
            "tpadeny", "tpdeny", "tpacancel", "tpatoggle", "tpaheretoggle", "tpauto", "settings",
            "chattoggle", "broadcasttoggle", "home", "homes", "sethome", "delhome", "shop", "spawner",
            "ah", "stats", "leaderboard", "baltop", "balancetop", "baltops", "scoreboard", "sb", "nv", "pay", "paytoggle",
            "paymsg", "balance", "bal", "money", "sell", "team", "billford", "crates");
    private static final List<String> DEFAULT_BLOCKED = List.of(
            "server", "queue", "pl", "plugins", "bukkit:pl", "bukkit:plugins", "version", "ver",
            "about", "icanhasbukkit", "minecraft:help", "bukkit:help");
    private static final List<String> DEFAULT_SAFE_AUTO_DISCOVER = List.of(
            "discord", "store", "rules", "help", "lobby", "hub", "msg", "w", "tell", "pm", "message",
            "r", "reply", "media", "baltop", "balancetop", "baltops", "balance", "bal", "money");
    private static final List<String> DEFAULT_RESERVED_DEFAULT = List.of(
            "ravencore", "rc", "ravencorevelo", "rcvelo", "ravenauction", "ravenauctionadmin",
            "auctionadmin", "cratemanager", "statsmanager", "combatmanager", "billfordpreset",
            "setspawn", "setafk", "tool", "worldedit", "we", "brush", "br", "wand", "pos1",
            "pos2", "hpos1", "hpos2", "copy", "paste", "schem", "schematic", "undo", "redo",
            "replace", "set", "walls", "sphere", "hsphere", "cyl", "hcyl", "pyramid",
            "hpyramid", "drain", "fixwater", "fixlava", "region", "rg", "lp", "luckperms",
            "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "stop", "restart",
            "reload", "timings", "spark", "paper", "bukkit", "minecraft");

    private final File file;
    private Map<String, Object> raw;
    private boolean enabled;
    private String bypassPermission;
    private String denyMessage;
    private boolean autoDiscover;
    private String autoDiscoverGroup;
    private boolean saveDiscovered;
    private boolean strictExecution;
    private boolean blockNamespaceCommands;
    private String namespaceBypassPermission;
    private Set<String> blockedCommands;
    private Set<String> safeAutoDiscoverCommands;
    private Set<String> reservedDefaultCommands;
    private List<String> groupOrder;
    private Map<String, CommandGroup> groups;

    CommandVisibilityConfig(File file) {
        this.file = file;
    }

    void load() {
        try (FileInputStream input = new FileInputStream(file)) {
            Object loaded = new Yaml().load(input);
            raw = loaded instanceof Map<?, ?> map ? castMap(map) : new LinkedHashMap<>();
        } catch (IOException e) {
            raw = new LinkedHashMap<>();
        }
        if (ensureDefaults()) {
            save();
        }

        enabled = bool("enabled", true);
        bypassPermission = string("bypass-permission", "raven.proxycommandfilter.bypass");
        denyMessage = string("deny-message", "&#D90429This command is not available.");
        autoDiscover = bool("auto-discover.enabled", true);
        autoDiscoverGroup = normalize(string("auto-discover.target-group", "default"));
        saveDiscovered = bool("auto-discover.save-to-config", true);
        strictExecution = bool("strict-execution", true);
        blockNamespaceCommands = bool("block-namespace-commands", true);
        namespaceBypassPermission = string("namespace-bypass-permission", "raven.proxycommandfilter.namespace");
        blockedCommands = normalizeCommands(list("blocked-commands"));
        safeAutoDiscoverCommands = normalizeCommands(list("safe-auto-discover-commands"));
        reservedDefaultCommands = normalizeCommands(list("reserved-default-commands"));
        groupOrder = list("group-order").stream().map(CommandVisibilityConfig::normalize).filter(s -> !s.isBlank()).toList();
        groups = readGroups();
    }

    synchronized boolean addCommand(String group, String command) {
        String normalizedGroup = normalize(group);
        String normalizedCommand = normalize(command);
        if (normalizedGroup.isBlank() || normalizedCommand.isBlank()) {
            return false;
        }
        Map<String, Object> groupsMap = mutableMap(raw.computeIfAbsent("groups", ignored -> new LinkedHashMap<>()));
        Map<String, Object> groupMap = mutableMap(groupsMap.computeIfAbsent(normalizedGroup, ignored -> defaultGroupMap(normalizedGroup)));
        List<String> commands = mutableStringList(groupMap.computeIfAbsent("commands", ignored -> new ArrayList<String>()));
        if (containsNormalized(commands, normalizedCommand)) {
            return false;
        }
        commands.add(normalizedCommand);
        groupMap.put("commands", commands);
        groupsMap.put(normalizedGroup, groupMap);
        raw.put("groups", groupsMap);
        save();
        load();
        return true;
    }

    synchronized boolean removeCommand(String group, String command) {
        String normalizedGroup = normalize(group);
        String normalizedCommand = normalize(command);
        Map<String, Object> groupsMap = mutableMap(raw.get("groups"));
        Map<String, Object> groupMap = mutableMap(groupsMap.get(normalizedGroup));
        List<String> commands = mutableStringList(groupMap.get("commands"));
        boolean removed = commands.removeIf(value -> normalize(value).equals(normalizedCommand));
        if (!removed) {
            return false;
        }
        groupMap.put("commands", commands);
        groupsMap.put(normalizedGroup, groupMap);
        raw.put("groups", groupsMap);
        save();
        load();
        return true;
    }

    synchronized boolean addBlockedCommand(String command) {
        String normalizedCommand = normalize(command);
        List<String> commands = mutableStringList(raw.computeIfAbsent("blocked-commands", ignored -> new ArrayList<String>()));
        if (containsNormalized(commands, normalizedCommand)) {
            return false;
        }
        commands.add(normalizedCommand);
        raw.put("blocked-commands", commands);
        save();
        load();
        return true;
    }

    synchronized boolean removeBlockedCommand(String command) {
        String normalizedCommand = normalize(command);
        List<String> commands = mutableStringList(raw.get("blocked-commands"));
        boolean removed = commands.removeIf(value -> normalize(value).equals(normalizedCommand));
        if (!removed) {
            return false;
        }
        raw.put("blocked-commands", commands);
        save();
        load();
        return true;
    }

    synchronized void discover(Collection<String> commands) {
        if (!autoDiscover || !saveDiscovered) {
            return;
        }
        boolean changed = false;
        for (String command : commands) {
            String normalized = normalize(command);
            if (normalized.isBlank()
                    || blockedCommands.contains(normalized)
                    || reservedDefaultCommands.contains(normalized)
                    || !safeAutoDiscoverCommands.contains(normalized)
                    || normalized.contains(":")) {
                continue;
            }
            changed |= addCommandRaw(autoDiscoverGroup, normalized);
        }
        if (changed) {
            save();
            load();
        }
    }

    Set<String> visibleCommands(String group) {
        Set<String> visible = new LinkedHashSet<>();
        collectCommands(normalize(group), visible, new LinkedHashSet<>());
        visible.removeAll(blockedCommands);
        return visible;
    }

    String groupFor(com.velocitypowered.api.proxy.Player player) {
        for (String group : groupOrder) {
            CommandGroup definition = groups.get(group);
            if (definition == null) {
                continue;
            }
            String permission = definition.permission();
            if (permission == null || permission.isBlank() || player.hasPermission(permission)) {
                return group;
            }
        }
        return "default";
    }

    boolean canBypass(com.velocitypowered.api.proxy.Player player) {
        return hasPermission(player, bypassPermission) || player.hasPermission("*");
    }

    boolean canBypassNamespace(com.velocitypowered.api.proxy.Player player) {
        return hasPermission(player, namespaceBypassPermission) || canBypass(player);
    }

    boolean enabled() { return enabled; }
    boolean strictExecution() { return strictExecution; }
    boolean blockNamespaceCommands() { return blockNamespaceCommands; }
    String denyMessage() { return denyMessage; }
    Set<String> blockedCommands() { return blockedCommands; }
    List<String> groupOrder() { return groupOrder; }
    Map<String, CommandGroup> groups() { return groups; }

    private boolean addCommandRaw(String group, String command) {
        Map<String, Object> groupsMap = mutableMap(raw.computeIfAbsent("groups", ignored -> new LinkedHashMap<>()));
        Map<String, Object> groupMap = mutableMap(groupsMap.computeIfAbsent(group, ignored -> defaultGroupMap(group)));
        List<String> commands = mutableStringList(groupMap.computeIfAbsent("commands", ignored -> new ArrayList<String>()));
        if (containsNormalized(commands, command)) {
            return false;
        }
        commands.add(command);
        groupMap.put("commands", commands);
        groupsMap.put(group, groupMap);
        raw.put("groups", groupsMap);
        return true;
    }

    private void collectCommands(String group, Set<String> output, Set<String> seen) {
        if (!seen.add(group)) {
            return;
        }
        CommandGroup definition = groups.get(group);
        if (definition == null) {
            return;
        }
        for (String inherited : definition.inherits()) {
            collectCommands(inherited, output, seen);
        }
        output.addAll(definition.commands());
    }

    private Map<String, CommandGroup> readGroups() {
        Map<String, Object> groupsMap = mutableMap(nested("groups"));
        Map<String, CommandGroup> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : groupsMap.entrySet()) {
            String name = normalize(entry.getKey());
            Map<String, Object> section = mutableMap(entry.getValue());
            result.put(name, new CommandGroup(
                    name,
                    string(section.get("permission"), ""),
                    mutableStringList(section.get("inherits")).stream().map(CommandVisibilityConfig::normalize).toList(),
                    normalizeCommands(mutableStringList(section.get("commands")))
            ));
        }
        result.putIfAbsent("default", new CommandGroup("default", "", List.of(), Set.of()));
        return Map.copyOf(result);
    }

    private boolean ensureDefaults() {
        boolean changed = false;
        changed |= putIfMissing(raw, "enabled", true);
        changed |= putIfMissing(raw, "bypass-permission", "raven.proxycommandfilter.bypass");
        changed |= putIfMissing(raw, "deny-message", "&#D90429This command is not available.");
        changed |= putIfMissing(raw, "strict-execution", true);
        changed |= putIfMissing(raw, "block-namespace-commands", true);
        changed |= putIfMissing(raw, "namespace-bypass-permission", "raven.proxycommandfilter.namespace");

        Map<String, Object> auto = mutableMap(raw.get("auto-discover"));
        changed |= putIfMissing(auto, "enabled", true);
        changed |= putIfMissing(auto, "target-group", "default");
        changed |= putIfMissing(auto, "save-to-config", false);
        raw.put("auto-discover", auto);

        changed |= mergeList("blocked-commands", DEFAULT_BLOCKED);
        changed |= mergeList("safe-auto-discover-commands", DEFAULT_SAFE_AUTO_DISCOVER);
        changed |= mergeList("reserved-default-commands", DEFAULT_RESERVED_DEFAULT);
        changed |= mergeList("group-order", DEFAULT_GROUP_ORDER);

        Map<String, Object> groupsMap = mutableMap(raw.get("groups"));
        changed |= ensureGroup(groupsMap, "default", "", List.of(), DEFAULT_COMMANDS);
        changed |= ensureGroup(groupsMap, "custom", "raven.proxycommandfilter.group.custom", List.of("default"), List.of());
        changed |= ensureGroup(groupsMap, "vip", "raven.proxycommandfilter.group.vip", List.of("default"), List.of());
        changed |= ensureGroup(groupsMap, "mvp", "raven.proxycommandfilter.group.mvp", List.of("vip"), List.of());
        changed |= ensureGroup(groupsMap, "titan", "raven.proxycommandfilter.group.titan", List.of("mvp"), List.of());
        changed |= ensureGroup(groupsMap, "supreme", "raven.proxycommandfilter.group.supreme", List.of("titan"), List.of());
        changed |= ensureGroup(groupsMap, "raven", "raven.proxycommandfilter.group.raven", List.of("supreme"),
                List.of("ravencore", "rc", "ravencorevelo", "rcvelo", "ravenauction", "cratemanager",
                        "statsmanager", "combatmanager", "billfordpreset"));
        changed |= removeReservedDefaultCommands(groupsMap);
        raw.put("groups", groupsMap);
        return changed;
    }

    private boolean removeReservedDefaultCommands(Map<String, Object> groupsMap) {
        Map<String, Object> group = mutableMap(groupsMap.get("default"));
        List<String> commands = mutableStringList(group.get("commands"));
        Set<String> reserved = normalizeCommands(mutableStringList(raw.get("reserved-default-commands")));
        boolean changed = commands.removeIf(command -> reserved.contains(normalize(command)));
        if (changed) {
            group.put("commands", commands);
            groupsMap.put("default", group);
        }
        return changed;
    }

    private boolean ensureGroup(Map<String, Object> groupsMap, String name, String permission,
                                List<String> inherits, List<String> commands) {
        boolean changed = false;
        Map<String, Object> group = mutableMap(groupsMap.get(name));
        changed |= putIfMissing(group, "permission", permission);
        changed |= putIfMissing(group, "inherits", new ArrayList<>(inherits));
        changed |= putIfMissing(group, "commands", new ArrayList<String>());
        List<String> existing = mutableStringList(group.get("commands"));
        for (String command : commands) {
            if (!containsNormalized(existing, command)) {
                existing.add(command);
                changed = true;
            }
        }
        group.put("commands", existing);
        groupsMap.put(name, group);
        return changed;
    }

    private boolean mergeList(String path, List<String> defaults) {
        List<String> values = mutableStringList(raw.get(path));
        boolean changed = false;
        for (String value : defaults) {
            if (!containsNormalized(values, value)) {
                values.add(value);
                changed = true;
            }
        }
        raw.put(path, values);
        return changed;
    }

    private boolean putIfMissing(Map<String, Object> map, String key, Object value) {
        if (map.containsKey(key)) {
            return false;
        }
        map.put(key, value);
        return true;
    }

    private void save() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        try (FileWriter writer = new FileWriter(file)) {
            new Yaml(options).dump(raw, writer);
        } catch (IOException ignored) {
        }
    }

    private boolean bool(String path, boolean def) {
        Object value = nested(path);
        return value instanceof Boolean b ? b : def;
    }

    private String string(String path, String def) {
        return string(nested(path), def);
    }

    private String string(Object value, String def) {
        return value == null ? def : value.toString();
    }

    private List<String> list(String path) {
        return mutableStringList(nested(path));
    }

    private Object nested(String path) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = raw;
        for (int i = 0; i < keys.length - 1; i++) {
            Object next = current.get(keys[i]);
            if (!(next instanceof Map<?, ?> map)) {
                return null;
            }
            current = castMap(map);
        }
        return current.get(keys[keys.length - 1]);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) castMap(map));
    }

    private static List<String> mutableStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return result;
    }

    private static Map<String, Object> defaultGroupMap(String group) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("permission", group.equals("default") ? "" : "raven.proxycommandfilter.group." + group);
        section.put("inherits", group.equals("default") ? List.of() : List.of("default"));
        section.put("commands", new ArrayList<String>());
        return section;
    }

    private static Set<String> normalizeCommands(Collection<String> commands) {
        Set<String> result = new LinkedHashSet<>();
        for (String command : commands) {
            String normalized = normalize(command);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.startsWith("/")) {
            value = value.substring(1).trim();
        }
        int space = value.indexOf(' ');
        if (space >= 0) {
            value = value.substring(0, space);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsNormalized(List<String> values, String command) {
        return values.stream().map(CommandVisibilityConfig::normalize).anyMatch(command::equals);
    }

    private static boolean hasPermission(com.velocitypowered.api.proxy.Player player, String permission) {
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }
}
