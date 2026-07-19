package com.kammoun.raven.velocity.proxycommandfilter;

import com.kammoun.raven.library.module.VModule;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class ProxyCommandFilterModule extends VModule {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private static final List<Field> COMMAND_NODE_MAP_FIELDS = findCommandNodeMapFields();

    private String bypassPermission;
    private String denyMessage;
    private Set<String> blockedCommands = Set.of();

    public ProxyCommandFilterModule() {
        super("proxycommandfilter");
    }

    @Override
    public void onModuleEnable() {
        bypassPermission = getString("bypass-permission", "raven.proxycommandfilter.bypass");
        denyMessage = getString("deny-message", "&cYou do not have permission to use this command.");
        blockedCommands = normalizeCommands(getStringList("blocked-commands"));

        if (COMMAND_NODE_MAP_FIELDS.isEmpty()) {
            getLogger().warning("No Brigadier command maps were detected. Command hiding may not work on this runtime.");
        }

        registerListener(this);
        getLogger().info("ProxyCommandFilter enabled. Blocked commands: " + blockedCommands);
    }

    @Override
    public void onModuleDisable() {
        blockedCommands = Set.of();
    }

    public void reload() {
        reloadConfig();
        bypassPermission = getString("bypass-permission", "raven.proxycommandfilter.bypass");
        denyMessage = getString("deny-message", "&cYou do not have permission to use this command.");
        blockedCommands = normalizeCommands(getStringList("blocked-commands"));
    }

    @Subscribe
    public void onPlayerAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (canBypass(event.getPlayer())) {
            return;
        }

        RootCommandNode<?> rootNode = event.getRootNode();
        for (String command : blockedCommands) {
            removeCommandNode(rootNode, command);
        }
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player) || canBypass(player)) {
            return;
        }

        String commandName = extractCommandName(event.getCommand());
        if (!blockedCommands.contains(commandName)) {
            return;
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());
        player.sendMessage(deserialize(denyMessage));
    }

    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        if (canBypass(event.getPlayer())) {
            return;
        }

        event.getSuggestions().removeIf(this::isBlockedCommand);
    }

    private boolean canBypass(Player player) {
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) {
            return true;
        }
        return player.hasPermission("*");
    }

    private boolean isBlockedCommand(String raw) {
        return blockedCommands.contains(extractCommandName(raw));
    }

    private String extractCommandName(String raw) {
        if (raw == null) {
            return "";
        }

        String normalized = raw.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }

        if (normalized.isEmpty()) {
            return "";
        }

        int firstSpace = normalized.indexOf(' ');
        if (firstSpace >= 0) {
            normalized = normalized.substring(0, firstSpace);
        }

        return normalized.toLowerCase(Locale.ROOT);
    }

    private Set<String> normalizeCommands(Collection<String> rawCommands) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String command : rawCommands) {
            String normalizedCommand = extractCommandName(command);
            if (!normalizedCommand.isEmpty()) {
                normalized.add(normalizedCommand);
            }
        }
        return normalized;
    }

    private void removeCommandNode(RootCommandNode<?> rootNode, String commandName) {
        try {
            for (Field field : COMMAND_NODE_MAP_FIELDS) {
                removeFromNodeMap(field, rootNode, commandName);
            }
        } catch (IllegalAccessException e) {
            getLogger().log(Level.WARNING, "Failed to remove command from Brigadier tree: " + commandName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void removeFromNodeMap(Field field, RootCommandNode<?> rootNode, String commandName)
            throws IllegalAccessException {
        Map<String, CommandNode<?>> commandMap = (Map<String, CommandNode<?>>) field.get(rootNode);
        commandMap.remove(commandName);
    }

    private Component deserialize(String message) {
        return LEGACY_SERIALIZER.deserialize(message);
    }

    private static List<Field> findCommandNodeMapFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : CommandNode.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                fields.add(field);
            } catch (Exception ignored) {
                // Ignore inaccessible fields and keep any compatible ones we can reach.
            }
        }
        return fields;
    }
}
