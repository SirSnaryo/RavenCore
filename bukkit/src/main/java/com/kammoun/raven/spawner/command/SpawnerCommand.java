package com.kammoun.raven.spawner.command;

import com.kammoun.raven.library.command.KCommand;
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
            sender.sendMessage("§cUsage: /spawner give <player> <type> <stack-size>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }

        SpawnerType type = module.getRegistry().get(args[2]);
        if (type == null) {
            sender.sendMessage("§cUnknown spawner type '§f" + args[2] + "§c'. Use /spawner list.");
            return;
        }

        int stackSize;
        try {
            stackSize = Integer.parseInt(args[3]);
            if (stackSize <= 0) { sender.sendMessage("§cStack size must be positive."); return; }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: §f" + args[3]);
            return;
        }

        ItemStack item = type.createItem(stackSize);
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        } else {
            target.getInventory().addItem(item);
        }
        sender.sendMessage("§aGave §f" + target.getName() + " §ax1 §f" + type.getDisplayName()
                + " §a(stack §f" + stackSize + "§a).");
    }

    private void handleReload(CommandSender sender) {
        module.reload();
        sender.sendMessage("§aSpawner configuration reloaded.");
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("§e§lAvailable spawner types:");
        for (SpawnerType type : module.getRegistry().all()) {
            sender.sendMessage("  §7- §f" + type.getId() + " §7(§f" + type.getDisplayName() + "§7)");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e§lSpawner Commands:");
        sender.sendMessage("§7/spawner give <player> <type> <stack-size> §f- Give a spawner item");
        sender.sendMessage("§7/spawner list §f- List all spawner types");
        sender.sendMessage("§7/spawner reload §f- Reload configuration");
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
