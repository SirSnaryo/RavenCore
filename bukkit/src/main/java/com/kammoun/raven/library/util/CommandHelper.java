package com.kammoun.raven.library.util;

import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class CommandHelper {

    private CommandHelper() {}

    public static void executeCommand(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty() || player == null) return;
        for (String command : commands) {
            final String processed = command
                    .replace("%player%", player.getName())
                    .replace("%player_uuid%", player.getUniqueId().toString());
            SchedulerUtil.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed));
        }
    }

    public static void executeCommand(Player player, String command) {
        executeCommand(player, List.of(command));
    }
}
