package com.kammoun.raven.velocity.proxycommandfilter;

import com.kammoun.raven.library.module.VModule;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.event.PostOrder;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class ProxyCommandFilterModule extends VModule {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final List<Field> COMMAND_NODE_MAP_FIELDS = findCommandNodeMapFields();

    private CommandVisibilityConfig visibility;
    private final Set<String> discoveredCommands = new LinkedHashSet<>();

    public ProxyCommandFilterModule() {
        super("proxycommandfilter");
    }

    @Override
    public void onModuleEnable() {
        visibility = new CommandVisibilityConfig(new java.io.File(moduleDataFolder, "config.yml"));
        visibility.load();
        if (COMMAND_NODE_MAP_FIELDS.isEmpty()) {
            getLogger().warning("No Brigadier command maps were detected. Command hiding may not work on this runtime.");
        }
        registerListener(this);
        getLogger().info("ProxyCommandFilter enabled. Groups: " + visibility.groupOrder());
    }

    @Override
    public void onModuleDisable() {
        discoveredCommands.clear();
    }

    @Override
    public void reload() {
        reloadConfig();
        visibility.load();
    }

    @Subscribe
    public void onPlayerAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (!enabled() || visibility.canBypass(event.getPlayer())) {
            return;
        }

        Set<String> available = collectRootCommands(event.getRootNode());
        discover(available);

        Set<String> visible = visibleCommands(event.getPlayer());
        RootCommandNode<?> rootNode = event.getRootNode();
        for (String command : available) {
            if (shouldHide(event.getPlayer(), command, visible)) {
                removeCommandNode(rootNode, command);
            }
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!enabled() || !(event.getCommandSource() instanceof Player player) || visibility.canBypass(player)) {
            return;
        }

        String command = extractCommandName(event.getCommand());
        if (!visibility.strictExecution() && !visibility.blockedCommands().contains(command)) {
            return;
        }

        if (shouldHide(player, command, visibleCommands(player))) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(deserialize(visibility.denyMessage()));
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!enabled() || visibility.canBypass(event.getPlayer())) {
            return;
        }
        if (!isRootCommandCompletion(event.getPartialMessage())) {
            return;
        }

        Set<String> visible = visibleCommands(event.getPlayer());
        event.getSuggestions().removeIf(raw -> shouldHide(event.getPlayer(), extractCommandName(raw), visible));
    }

    public List<String> groups() {
        return new ArrayList<>(visibility.groups().keySet());
    }

    public List<String> commands(String group) {
        return new ArrayList<>(visibility.visibleCommands(group));
    }

    public List<String> blockedCommands() {
        return new ArrayList<>(visibility.blockedCommands());
    }

    public List<String> discoveredCommands() {
        return new ArrayList<>(discoveredCommands);
    }

    public boolean addCommand(String group, String command) {
        return visibility.addCommand(group, command);
    }

    public boolean removeCommand(String group, String command) {
        return visibility.removeCommand(group, command);
    }

    public boolean addBlockedCommand(String command) {
        return visibility.addBlockedCommand(command);
    }

    public boolean removeBlockedCommand(String command) {
        return visibility.removeBlockedCommand(command);
    }

    private boolean enabled() {
        return visibility != null && visibility.enabled();
    }

    private Set<String> visibleCommands(Player player) {
        return visibility.visibleCommands(visibility.groupFor(player));
    }

    private boolean shouldHide(Player player, String command, Set<String> visible) {
        if (command.isBlank()) {
            return false;
        }
        if (visibility.blockedCommands().contains(command)) {
            return true;
        }
        if (visibility.blockNamespaceCommands() && command.contains(":") && !visibility.canBypassNamespace(player)) {
            return true;
        }
        return !visible.contains(command);
    }

    private boolean isRootCommandCompletion(String partialMessage) {
        if (partialMessage == null) {
            return false;
        }
        String trimmed = partialMessage.stripLeading();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        return !trimmed.substring(1).contains(" ");
    }

    private void discover(Collection<String> commands) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String command : commands) {
            String value = extractCommandName(command);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        discoveredCommands.addAll(normalized);
        visibility.discover(normalized);
    }

    private Set<String> collectRootCommands(RootCommandNode<?> rootNode) {
        Set<String> commands = new LinkedHashSet<>();
        for (CommandNode<?> node : rootNode.getChildren()) {
            commands.add(extractCommandName(node.getName()));
        }
        return commands;
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

    private String extractCommandName(String raw) {
        return CommandVisibilityConfig.normalize(raw);
    }

    private Component deserialize(String message) {
        return LEGACY_SERIALIZER.deserialize(toLegacyHex(message));
    }

    private String toLegacyHex(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(builder);
        return builder.toString();
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
            }
        }
        return fields;
    }
}
