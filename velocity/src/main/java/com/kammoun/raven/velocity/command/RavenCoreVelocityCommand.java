package com.kammoun.raven.velocity.command;

import com.kammoun.raven.velocity.RavenVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RavenCoreVelocityCommand implements SimpleCommand {

    private final RavenVelocity plugin;

    public RavenCoreVelocityCommand(RavenVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || args[0].equalsIgnoreCase("version")) {
            invocation.source().sendMessage(Component.text(
                    "RavenCore Velocity " + plugin.getVersion()
                            + " | proxy " + plugin.getString("network.server-id", "velocity-proxy"),
                    NamedTextColor.LIGHT_PURPLE
            ));
            return;
        }

        if (args[0].equalsIgnoreCase("modules")) {
            invocation.source().sendMessage(Component.text(
                    "Modules: " + String.join(", ", plugin.reloadableModuleNames()),
                    NamedTextColor.YELLOW
            ));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!invocation.source().hasPermission("raven.velocity.admin")) {
                invocation.source().sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return;
            }
            if (args.length < 2) {
                invocation.source().sendMessage(Component.text(
                        "Usage: /ravencorevelo reload <module|all>",
                        NamedTextColor.RED
                ));
                return;
            }

            String module = args[1].toLowerCase(Locale.ROOT);
            if (module.equals("all")) {
                plugin.reloadAllModulesSafely();
                invocation.source().sendMessage(Component.text("Velocity safe reload scheduled for all modules.", NamedTextColor.GREEN));
                return;
            }

            if (!plugin.reloadModuleSafely(module)) {
                invocation.source().sendMessage(Component.text("Unknown module. Use /ravencorevelo modules.", NamedTextColor.RED));
                return;
            }

            invocation.source().sendMessage(Component.text("Velocity safe reload scheduled for " + module + ".", NamedTextColor.GREEN));
            return;
        }

        if (args[0].equalsIgnoreCase("tab")) {
            handleTab(invocation);
            return;
        }

        invocation.source().sendMessage(Component.text("/ravencorevelo version", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/ravencorevelo modules", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/ravencorevelo reload <module|all>", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/ravencorevelo tab <status|groups|list|add|remove|block|unblock|blocked|discovered|reload>", NamedTextColor.YELLOW));
    }

    private void handleTab(Invocation invocation) {
        if (!invocation.source().hasPermission("raven.velocity.admin")) {
            invocation.source().sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (plugin.getProxyCommandFilterModule() == null) {
            invocation.source().sendMessage(Component.text("Proxy command filter is not enabled.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            invocation.source().sendMessage(Component.text("AntiTab groups: "
                    + String.join(", ", plugin.getProxyCommandFilterModule().groups()), NamedTextColor.YELLOW));
            invocation.source().sendMessage(Component.text("Blocked: "
                    + plugin.getProxyCommandFilterModule().blockedCommands().size()
                    + " | discovered: " + plugin.getProxyCommandFilterModule().discoveredCommands().size(),
                    NamedTextColor.YELLOW));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.getProxyCommandFilterModule().reload();
                invocation.source().sendMessage(Component.text("AntiTab reloaded.", NamedTextColor.GREEN));
            }
            case "groups" -> invocation.source().sendMessage(Component.text(
                    "Groups: " + String.join(", ", plugin.getProxyCommandFilterModule().groups()),
                    NamedTextColor.YELLOW));
            case "list" -> {
                if (args.length < 3) {
                    invocation.source().sendMessage(Component.text("Usage: /ravencorevelo tab list <group>", NamedTextColor.RED));
                    return;
                }
                invocation.source().sendMessage(Component.text(args[2] + ": "
                        + String.join(", ", plugin.getProxyCommandFilterModule().commands(args[2])), NamedTextColor.YELLOW));
            }
            case "add" -> {
                if (args.length < 4) {
                    invocation.source().sendMessage(Component.text("Usage: /ravencorevelo tab add <group> <command>", NamedTextColor.RED));
                    return;
                }
                boolean changed = plugin.getProxyCommandFilterModule().addCommand(args[2], args[3]);
                invocation.source().sendMessage(Component.text(changed ? "Command added." : "Command already exists.", changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "remove" -> {
                if (args.length < 4) {
                    invocation.source().sendMessage(Component.text("Usage: /ravencorevelo tab remove <group> <command>", NamedTextColor.RED));
                    return;
                }
                boolean changed = plugin.getProxyCommandFilterModule().removeCommand(args[2], args[3]);
                invocation.source().sendMessage(Component.text(changed ? "Command removed." : "Command was not in that group.", changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "block" -> {
                if (args.length < 3) {
                    invocation.source().sendMessage(Component.text("Usage: /ravencorevelo tab block <command>", NamedTextColor.RED));
                    return;
                }
                boolean changed = plugin.getProxyCommandFilterModule().addBlockedCommand(args[2]);
                invocation.source().sendMessage(Component.text(changed ? "Command blocked." : "Command already blocked.", changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "unblock" -> {
                if (args.length < 3) {
                    invocation.source().sendMessage(Component.text("Usage: /ravencorevelo tab unblock <command>", NamedTextColor.RED));
                    return;
                }
                boolean changed = plugin.getProxyCommandFilterModule().removeBlockedCommand(args[2]);
                invocation.source().sendMessage(Component.text(changed ? "Command unblocked." : "Command was not blocked.", changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "blocked" -> invocation.source().sendMessage(Component.text(
                    "Blocked: " + String.join(", ", plugin.getProxyCommandFilterModule().blockedCommands()),
                    NamedTextColor.YELLOW));
            case "discovered" -> invocation.source().sendMessage(Component.text(
                    "Discovered: " + String.join(", ", plugin.getProxyCommandFilterModule().discoveredCommands()),
                    NamedTextColor.YELLOW));
            default -> invocation.source().sendMessage(Component.text(
                    "Usage: /ravencorevelo tab <status|groups|list|add|remove|block|unblock|blocked|discovered|reload>",
                    NamedTextColor.RED));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            return List.of("version", "modules", "reload", "tab").stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")
                && invocation.source().hasPermission("raven.velocity.admin")) {
            List<String> values = new ArrayList<>(plugin.reloadableModuleNames());
            values.add("all");
            String lower = args[1].toLowerCase(Locale.ROOT);
            return values.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                    .toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("tab")
                && invocation.source().hasPermission("raven.velocity.admin")) {
            return suggestTab(args);
        }
        return List.of();
    }

    private List<String> suggestTab(String[] args) {
        if (plugin.getProxyCommandFilterModule() == null) {
            return List.of();
        }
        if (args.length == 2) {
            String lower = args[1].toLowerCase(Locale.ROOT);
            return List.of("status", "groups", "list", "add", "remove", "block", "unblock", "blocked", "discovered", "reload")
                    .stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (args.length == 3 && List.of("list", "add", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
            String lower = args[2].toLowerCase(Locale.ROOT);
            return plugin.getProxyCommandFilterModule().groups().stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("remove")) {
            String lower = args[3].toLowerCase(Locale.ROOT);
            return plugin.getProxyCommandFilterModule().commands(args[2]).stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
            String lower = args[3].toLowerCase(Locale.ROOT);
            return plugin.getProxyCommandFilterModule().discoveredCommands().stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("unblock")) {
            String lower = args[2].toLowerCase(Locale.ROOT);
            return plugin.getProxyCommandFilterModule().blockedCommands().stream()
                    .filter(value -> value.startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
