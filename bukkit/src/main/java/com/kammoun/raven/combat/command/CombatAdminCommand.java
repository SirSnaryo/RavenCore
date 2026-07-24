package com.kammoun.raven.combat.command;

import com.kammoun.raven.combat.CombatModule;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.redis.event.CombatTagEvent;
import com.kammoun.raven.library.redis.event.CombatUntagEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CombatAdminCommand implements CommandExecutor, TabCompleter {
    private final CombatModule module;

    public CombatAdminCommand(CombatModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("raven.combat.admin")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (args.length < 2 || (!args[0].equalsIgnoreCase("tag") && !args[0].equalsIgnoreCase("untag"))) {
            sender.sendMessage("§c/combatmanager untag <player>");
            sender.sendMessage("§c/combatmanager tag <player> <opponent>");
            return true;
        }
        SchedulerUtil.runAsync(() -> {
            Optional<NetworkPlayerInfo> target = module.getApi().getNetworkPlayerManager().findPlayer(args[1]);
            if (target.isEmpty()) {
                reply(sender, "§cNetwork player not found: " + args[1]);
                return;
            }
            if (args[0].equalsIgnoreCase("untag")) {
                CombatUntagEvent event = new CombatUntagEvent();
                event.setPlayerUuid(target.get().getUuid().toString());
                event.setNotify(true);
                module.getApi().getNetworkEventBus().publish(event);
                reply(sender, "§aUntagged " + target.get().getPlayerName() + ".");
                return;
            }
            if (args.length < 3) {
                reply(sender, "§c/combatmanager tag <player> <opponent>");
                return;
            }
            Optional<NetworkPlayerInfo> opponent = module.getApi().getNetworkPlayerManager().findPlayer(args[2]);
            if (opponent.isEmpty()) {
                reply(sender, "§cNetwork player not found: " + args[2]);
                return;
            }
            long expiry = System.currentTimeMillis() + module.getCombatConfig().durationSeconds() * 1000L;
            publish(target.get(), opponent.get(), expiry);
            publish(opponent.get(), target.get(), expiry);
            reply(sender, "§aTagged " + target.get().getPlayerName() + " with "
                    + opponent.get().getPlayerName() + ".");
        });
        return true;
    }

    private void publish(NetworkPlayerInfo player, NetworkPlayerInfo opponent, long expiry) {
        CombatTagEvent event = new CombatTagEvent();
        event.setPlayerUuid(player.getUuid().toString());
        event.setOpponentUuid(opponent.getUuid().toString());
        event.setExpiresAt(expiry);
        module.getApi().getNetworkEventBus().publish(event);
    }

    private void reply(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            SchedulerUtil.execute(player, () -> player.sendMessage(message));
            return;
        }
        sender.sendMessage(message);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("tag", "untag"), args[0]);
        if (args.length == 2 || args.length == 3 && args[0].equalsIgnoreCase("tag")) {
            return filter(new ArrayList<>(module.getApi().getNetworkPlayerManager().getNetworkPlayerNames()),
                    args[args.length - 1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        return values.stream().filter(value -> value.toLowerCase().startsWith(lower)).toList();
    }
}
