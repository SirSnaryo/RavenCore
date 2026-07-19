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

        invocation.source().sendMessage(Component.text("/ravencorevelo version", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/ravencorevelo modules", NamedTextColor.YELLOW));
        invocation.source().sendMessage(Component.text("/ravencorevelo reload <module|all>", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            return List.of("version", "modules", "reload").stream()
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
        return List.of();
    }
}
