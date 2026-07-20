package com.kammoun.raven.spawner.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.spawner.SpawnerModule;
import com.kammoun.raven.spawner.model.SpawnerType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnerCommand extends KCommand {

    private final SpawnerModule module;

    public SpawnerCommand(SpawnerModule module) {
        super("spawner", "raven.spawner.admin", false);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) { sendHelp(sender); return; }

        switch (args[0].toLowerCase()) {
            case "give"   -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "list"   -> handleList(sender);
            default       -> sendHelp(sender);
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "&#D90429Usage: /spawner give <player> <type> <stack-size>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { send(sender, "&#D90429Player not found."); return; }

        SpawnerType type = module.getRegistry().get(args[2]);
        if (type == null) {
            send(sender, "&#D90429Unknown spawner type '&#FFFFFF" + args[2] + "&#D90429'. Use /spawner list.");
            return;
        }

        int stackSize;
        try {
            stackSize = Integer.parseInt(args[3]);
            if (stackSize <= 0) { send(sender, "&#D90429Stack size must be positive."); return; }
        } catch (NumberFormatException e) {
            send(sender, "&#D90429Invalid number: &#FFFFFF" + args[3]);
            return;
        }

        ItemStack item = type.createItem(stackSize);
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        } else {
            target.getInventory().addItem(item);
        }
        send(sender, "&#33FF66Gave &#FFFFFF" + target.getName() + " &#33FF66x1 &#FFFFFF" + type.getDisplayName()
                + " &#33FF66(stack &#FFFFFF" + stackSize + "&#33FF66).");
    }

    private void handleReload(CommandSender sender) {
        module.reload();
        send(sender, "&#33FF66Spawner configuration reloaded.");
    }

    private void handleList(CommandSender sender) {
        send(sender, "&#FFD447&lAvailable spawner types:");
        for (SpawnerType type : module.getRegistry().all()) {
            send(sender, "  &#7A7A7A- &#FFFFFF" + type.getId() + " &#7A7A7A(&#FFFFFF" + type.getDisplayName() + "&#7A7A7A)");
        }
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "&#FFD447&lSpawner Commands:");
        send(sender, "&#7A7A7A/spawner give <player> <type> <stack-size> &#FFFFFF- Give a spawner item");
        send(sender, "&#7A7A7A/spawner list &#FFFFFF- List all spawner types");
        send(sender, "&#7A7A7A/spawner reload &#FFFFFF- Reload configuration");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatFormatter.color(message));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("give", "list", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return module.getRegistry().all().stream()
                    .map(SpawnerType::getId)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return List.of("1", "10", "100", "1000");
        }
        return new ArrayList<>();
    }
}
