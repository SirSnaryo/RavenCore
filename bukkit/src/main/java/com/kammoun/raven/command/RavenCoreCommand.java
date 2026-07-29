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
            send(sender, "&#FF2B2BRavenCore &#7A7A7Aversion &#FFFFFF" + plugin.getDescription().getVersion()
                    + " &#7A7A7A| server &#FFFFFF" + plugin.getServerId()
                    + " &#7A7A7A| role &#FFFFFF" + plugin.getServerRole());
            return;
        }

        if (isModules(args[0])) {
            send(sender, "&#FFD447Modules: &#FFFFFF" + String.join("&#7A7A7A, &#FFFFFF", plugin.reloadableModuleNames()));
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("raven.core.admin")) {
                send(sender, "&#D90429You don't have permission to reload RavenCore modules.");
                return;
            }
            if (args.length < 2) {
                send(sender, "&#D90429Usage: /ravencore reload <module|all>");
                return;
            }

            String module = args[1].toLowerCase(Locale.ROOT);
            if (module.equals("all")) {
                plugin.reloadAllModulesSafely();
                send(sender, "&#33FF66Safe reload scheduled for all RavenCore modules.");
                return;
            }

            if (!plugin.reloadModuleSafely(module)) {
                send(sender, "&#D90429Unknown module. Use /ravencore modules.");
                return;
            }

            send(sender, "&#33FF66Safe reload scheduled for module &#FFFFFF" + module + "&#33FF66.");
            return;
        }

        send(sender, "&#FFD447/ravencore version &#7A7A7A- Show plugin version");
        send(sender, "&#FFD447/ravencore modules &#7A7A7A- List reloadable modules");
        send(sender, "&#FFD447/ravencore reload <module|all> &#7A7A7A- Safe reload");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("version", "modules", "module", "mods", "reload").stream()
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

    private boolean isModules(String input) {
        return input.equalsIgnoreCase("modules")
                || input.equalsIgnoreCase("module")
                || input.equalsIgnoreCase("modles")
                || input.equalsIgnoreCase("mods");
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
