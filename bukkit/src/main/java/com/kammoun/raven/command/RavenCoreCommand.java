package com.kammoun.raven.command;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RavenCoreCommand extends KCommand {

    private final RavenCore plugin;

    public RavenCoreCommand(RavenCore plugin) {
        super("ravencore", null, false);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("version")) {
            send(sender, "&5RavenCore &7version &f" + plugin.getDescription().getVersion()
                    + " &8| &7server &f" + plugin.getServerId()
                    + " &8| &7role &f" + plugin.getServerRole());
            return;
        }

        if (args[0].equalsIgnoreCase("modules")) {
            send(sender, "&eModules: &f" + String.join("&7, &f", plugin.reloadableModuleNames()));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("raven.core.admin")) {
                send(sender, "&cYou don't have permission to reload RavenCore modules.");
                return;
            }
            if (args.length < 2) {
                send(sender, "&cUsage: /ravencore reload <module|all>");
                return;
            }

            String module = args[1].toLowerCase(Locale.ROOT);
            if (module.equals("all")) {
                plugin.reloadAllModulesSafely();
                send(sender, "&aSafe reload scheduled for all RavenCore modules.");
                return;
            }

            if (!plugin.reloadModuleSafely(module)) {
                send(sender, "&cUnknown module. Use /ravencore modules.");
                return;
            }

            send(sender, "&aSafe reload scheduled for module &f" + module + "&a.");
            return;
        }

        send(sender, "&e/ravencore version &7- Show plugin version");
        send(sender, "&e/ravencore modules &7- List reloadable modules");
        send(sender, "&e/ravencore reload <module|all> &7- Safe reload");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("version", "modules", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("raven.core.admin")) {
            List<String> values = new ArrayList<>(plugin.reloadableModuleNames());
            values.add("all");
            String lower = args[1].toLowerCase(Locale.ROOT);
            return values.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                    .toList();
        }
        return List.of();
    }

    private void send(CommandSender sender, String message) {
        Runnable task = () -> sender.sendMessage(ChatFormatter.color(message));
        if (sender instanceof Player player) {
            SchedulerUtil.execute(player, task);
            return;
        }
        SchedulerUtil.run(task);
    }
}
