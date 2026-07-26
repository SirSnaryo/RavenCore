package com.kammoun.raven.stats.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.stats.gui.StatsMenu;
import com.kammoun.raven.stats.service.StatsService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class StatsCommand extends KCommand {
    private final StatsService service;
    private final java.util.function.Supplier<StatsMenu> menuFactory;

    public StatsCommand(StatsService service, java.util.function.Supplier<StatsMenu> menuFactory) {
        super("stats", "raven.stats", true);
        this.service = service;
        this.menuFactory = menuFactory;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player viewer = (Player) sender;
        String name = args.length == 0 ? viewer.getName() : args[0];
        if (!name.equalsIgnoreCase(viewer.getName()) && !viewer.hasPermission("raven.stats.others")) return;
        service.find(name).whenComplete((result, error) -> SchedulerUtil.execute(viewer, () -> {
            if (!viewer.isOnline()) return;
            if (error != null) {
                viewer.sendMessage(ChatFormatter.color("&cCould not load stats right now."));
            } else if (result.isEmpty()) {
                viewer.sendMessage(ChatFormatter.color("&cPlayer stats were not found."));
            } else {
                menuFactory.get().open(viewer, result.get());
            }
        }));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
