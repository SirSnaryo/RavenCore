package com.kammoun.raven.stats.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.stats.gui.StatsMenu;
import com.kammoun.raven.stats.ravs.RavsBalanceResolver;
import com.kammoun.raven.stats.service.StatsService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class StatsCommand extends KCommand {
    private final StatsService service;
    private final RavsBalanceResolver ravs;
    private final java.util.function.Supplier<StatsMenu> menuFactory;

    public StatsCommand(StatsService service, RavsBalanceResolver ravs,
                        java.util.function.Supplier<StatsMenu> menuFactory) {
        super("stats", "raven.stats", true);
        this.service = service;
        this.ravs = ravs;
        this.menuFactory = menuFactory;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player viewer = (Player) sender;
        String name = args.length == 0 ? viewer.getName() : args[0];
        if (!name.equalsIgnoreCase(viewer.getName()) && !viewer.hasPermission("raven.stats.others")) return;
        service.find(name).whenComplete((result, error) -> {
            if (error != null || result.isEmpty()) {
                SchedulerUtil.execute(viewer, () -> {
                    if (!viewer.isOnline()) return;
                    viewer.sendMessage(ChatFormatter.color(error != null
                            ? "&cCould not load stats right now."
                            : "&cPlayer stats were not found."));
                });
                return;
            }
            ravs.resolve(result.get().uuid()).whenComplete((balance, ravsError) ->
                    SchedulerUtil.execute(viewer, () -> {
                        if (!viewer.isOnline()) return;
                        menuFactory.get().open(viewer, result.get(), ravsError == null ? balance : 0L);
                    }));
        });
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
