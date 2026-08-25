package com.kammoun.raven.stats.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.library.network.NetworkPlayerManager;
import com.kammoun.raven.stats.gui.StatsMenu;
import com.kammoun.raven.stats.ravs.RavsBalanceResolver;
import com.kammoun.raven.stats.service.StatsService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class StatsCommand extends KCommand {
    private final StatsService service;
    private final RavsBalanceResolver ravs;
    private final NetworkPlayerManager networkPlayers;
    private final java.util.function.Supplier<StatsMenu> menuFactory;

    public StatsCommand(StatsService service, RavsBalanceResolver ravs, NetworkPlayerManager networkPlayers,
                        java.util.function.Supplier<StatsMenu> menuFactory) {
        super("stats", "raven.stats", true);
        this.service = service;
        this.ravs = ravs;
        this.networkPlayers = networkPlayers;
        this.menuFactory = menuFactory;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player viewer = (Player) sender;
        String name = args.length == 0 ? viewer.getName() : args[0];
        if (!name.equalsIgnoreCase(viewer.getName()) && !viewer.hasPermission("raven.stats.others")) {
            viewer.sendMessage(ChatFormatter.color("&cYou don't have permission to view other players' stats."));
            return;
        }
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
        if (args.length != 1 || !sender.hasPermission("raven.stats.others")) return List.of();
        if (networkPlayers != null) {
            return networkPlayers.completePlayerNames(args[0]);
        }
        String prefix = args[0].toLowerCase();
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
