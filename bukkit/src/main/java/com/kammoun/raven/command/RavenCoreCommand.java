package com.kammoun.raven.command;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
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

        if (isMemory(args[0])) {
            if (!sender.hasPermission("raven.core.admin")) {
                send(sender, "&#D90429You don't have permission to inspect RavenCore memory.");
                return;
            }
            sendMemory(sender);
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
        send(sender, "&#FFD447/ravencore memory &#7A7A7A- Show heap and thread usage");
        send(sender, "&#FFD447/ravencore reload <module|all> &#7A7A7A- Safe reload");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("version", "modules", "module", "mods", "memory", "mem", "ram", "reload").stream()
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

    private boolean isMemory(String input) {
        return input.equalsIgnoreCase("memory")
                || input.equalsIgnoreCase("mem")
                || input.equalsIgnoreCase("ram");
    }

    private void sendMemory(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        int liveThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        int peakThreads = ManagementFactory.getThreadMXBean().getPeakThreadCount();

        send(sender, "&#FF2B2BRavenCore Memory");
        send(sender, "&#7A7A7AHeap: &#FFFFFF" + mb(heap.getUsed()) + "MB&#7A7A7A / &#FFFFFF"
                + mb(heap.getMax()) + "MB &#7A7A7A(committed &#FFFFFF" + mb(heap.getCommitted()) + "MB&#7A7A7A)");
        send(sender, "&#7A7A7ANon-heap: &#FFFFFF" + mb(nonHeap.getUsed()) + "MB"
                + " &#7A7A7A(committed &#FFFFFF" + mb(nonHeap.getCommitted()) + "MB&#7A7A7A)");
        send(sender, "&#7A7A7ARuntime: free &#FFFFFF" + mb(runtime.freeMemory()) + "MB"
                + " &#7A7A7A| total &#FFFFFF" + mb(runtime.totalMemory()) + "MB"
                + " &#7A7A7A| max &#FFFFFF" + mb(runtime.maxMemory()) + "MB");
        send(sender, "&#7A7A7AThreads: live &#FFFFFF" + liveThreads
                + " &#7A7A7A| peak &#FFFFFF" + peakThreads
                + " &#7A7A7A| CPUs &#FFFFFF" + runtime.availableProcessors());
    }

    private long mb(long bytes) {
        if (bytes < 0L) {
            return -1L;
        }
        return bytes / (1024L * 1024L);
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
