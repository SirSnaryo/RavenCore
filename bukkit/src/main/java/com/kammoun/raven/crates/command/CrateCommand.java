package com.kammoun.raven.crates.command;

import com.kammoun.raven.crates.CratesModule;
import com.kammoun.raven.crates.gui.CrateEditorMenu;
import com.kammoun.raven.crates.gui.CrateMenu;
import com.kammoun.raven.crates.model.CrateDefinition;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class CrateCommand implements CommandExecutor, TabCompleter {
    private final CratesModule module;

    public CrateCommand(CratesModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "givecrate" -> giveCrate(sender, args);
            case "setkey", "set" -> setKey(sender, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            case "preview" -> preview(sender, args);
            case "keyall" -> keyAll(sender, args);
            case "edit" -> edit(sender, args);
            case "setblock" -> setBlock(sender, args);
            case "time" -> sender.sendMessage(ChatFormatter.color("&#FFD900Next crate key in &f"
                    + module.getTimerService().formattedTimeLeft()));
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatFormatter.color("&8&m------------------------------------"));
        sender.sendMessage(ChatFormatter.color("&#FFD900&lCRATES &8— &7Command Help"));
        sender.sendMessage(ChatFormatter.color("&8&m------------------------------------"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager give <player> <crate> [amount] &8— &7Give crate keys"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager givecrate <player> <crate> [amount] &8— &7Give physical crate"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager setkey <player> <crate> <amount> &8— &7Set keys"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager list &8— &7List crates"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager reload &8— &7Reload config"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager preview <crate> &8— &7Preview rewards"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager keyall <crate> [amount] &8— &7Give all online keys"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager edit <crate> &8— &7Open reward editor"));
        sender.sendMessage(ChatFormatter.color("  &#FFD900/cratemanager setblock <crate> &8— &7Set looked-at block"));
        sender.sendMessage(ChatFormatter.color("&8&m------------------------------------"));
    }

    private void list(CommandSender sender) {
        if (module.getConfigModel().crates().isEmpty()) {
            sender.sendMessage(ChatFormatter.color("&cNo crates configured!"));
            return;
        }
        sender.sendMessage(ChatFormatter.color("&b&lAvailable Crates &7(" + module.getConfigModel().crates().size() + ")"));
        module.getConfigModel().crates().values().forEach(crate ->
                sender.sendMessage(ChatFormatter.color("&7- &f" + crate.displayName() + " &7(ID: &b" + crate.id() + "&7)")));
    }

    private void setBlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatFormatter.color("&cThis command can only be used by players!"));
            return;
        }
        if (!player.hasPermission("raven.crates.admin")) return;
        if (args.length < 2) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /cratemanager setblock <crate>"));
            return;
        }
        CrateDefinition crate = module.getConfigModel().crate(args[1]);
        if (crate == null) {
            sender.sendMessage(ChatFormatter.color("&cCrate not found: " + args[1]));
            return;
        }
        RayTraceResult rayTrace = player.rayTraceBlocks(5, FluidCollisionMode.NEVER);
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            sender.sendMessage(ChatFormatter.color("&cYou must be looking at a block! (within 5 blocks)"));
            return;
        }
        Block targetBlock = rayTrace.getHitBlock();
        Location location = targetBlock.getLocation();
        if (module.getService().isCrate(location)) module.getService().removeCrate(player, location);
        module.getService().placeCrate(player, location, crate);
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raven.crates.admin")) return;
        if (args.length < 3) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /cratemanager give <player> <crate> [amount]"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String crateId = args[2];
        if (module.getConfigModel().crate(crateId) == null) {
            sender.sendMessage(ChatFormatter.color("&cCrate not found: " + crateId));
            return;
        }
        int amount = args.length >= 4 ? positive(args[3], 1) : 1;
        module.getService().addKeys(target.getUniqueId(), crateId, amount);
        sender.sendMessage(ChatFormatter.color("&aGave &f" + amount + " &a" + crateId + " key(s) to &f" + args[1]));
    }

    private void giveCrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raven.crates.admin")) return;
        if (args.length < 3) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /cratemanager givecrate <player> <crate> [amount]"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatFormatter.color("&cPlayer not found: " + args[1]));
            return;
        }
        CrateDefinition crate = module.getConfigModel().crate(args[2]);
        if (crate == null) {
            sender.sendMessage(ChatFormatter.color("&cCrate not found: " + args[2]));
            return;
        }
        int amount = args.length >= 4 ? positive(args[3], 1) : 1;
        ItemStack crateItem = crate.blockItem().clone();
        crateItem.setAmount(amount);
        HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(crateItem);
        leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage(ChatFormatter.color("&aGave &f" + amount + " &a" + crate.id() + " crate(s) to &f" + target.getName()));
    }

    private void setKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raven.crates.admin")) return;
        if (args.length < 4) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /cratemanager setkey <player> <crate> <amount>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String crateId = args[2];
        if (module.getConfigModel().crate(crateId) == null) {
            sender.sendMessage(ChatFormatter.color("&cCrate not found: " + crateId));
            return;
        }
        int amount = Math.max(0, positive(args[3], 0));
        module.getService().setKeys(target.getUniqueId(), crateId, amount);
        sender.sendMessage(ChatFormatter.color("&aSet &f" + args[1] + "'s &a" + crateId + " keys to &f" + amount));
    }

    private void keyAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raven.crates.admin")) return;
        if (args.length < 2) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /cratemanager keyall <crate> [amount]"));
            return;
        }
        String crateId = args[1];
        if (module.getConfigModel().crate(crateId) == null) {
            sender.sendMessage(ChatFormatter.color("&cCrate not found: " + crateId));
            return;
        }
        int amount = args.length >= 3 ? positive(args[2], 1) : 1;
        for (Player player : Bukkit.getOnlinePlayers()) {
            module.getService().addKeys(player.getUniqueId(), crateId, amount);
            player.sendMessage(ChatFormatter.color(module.getConfigModel().receivedKeyMessage()
                    .replace("%amount%", String.valueOf(amount)).replace("%crate%", crateId)));
        }
        sender.sendMessage(ChatFormatter.color("&aGave &f" + amount + " &a" + crateId + " key(s) to everyone online."));
    }

    private void preview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) return;
        CrateDefinition crate = module.getConfigModel().crate(args[1]);
        if (crate == null) return;
        new CrateMenu(crate, module.getService()).open(player);
    }

    private void edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatFormatter.color("&cThis command can only be used by players!"));
            return;
        }
        if (!player.hasPermission("raven.crates.admin")) return;
        if (args.length < 2) {
            sender.sendMessage(ChatFormatter.color("&cUsage: /cratemanager edit <crate>"));
            return;
        }
        CrateDefinition crate = module.getConfigModel().crate(args[1]);
        File file = module.getConfigModel().crateFile(args[1]);
        if (crate == null || file == null) {
            sender.sendMessage(ChatFormatter.color("&cCrate not found: " + args[1]));
            return;
        }
        new CrateEditorMenu(module, crate, player, file).open();
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("raven.crates.admin")) return;
        long start = System.currentTimeMillis();
        module.reload();
        sender.sendMessage(ChatFormatter.color("&aRaven Crates reloaded in &f" + (System.currentTimeMillis() - start) + "ms"));
    }

    private int positive(String raw, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("give", "givecrate", "setkey", "list", "reload", "preview", "keyall", "edit", "setblock", "time");
        }
        if (args.length == 2 && List.of("setblock", "preview", "keyall", "edit").contains(args[0].toLowerCase())) {
            return new ArrayList<>(module.getConfigModel().crates().keySet());
        }
        if (args.length == 3 && List.of("give", "givecrate", "setkey").contains(args[0].toLowerCase())) {
            return new ArrayList<>(module.getConfigModel().crates().keySet());
        }
        return List.of();
    }
}
