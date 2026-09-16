package com.kammoun.raven.borders.command;

import com.kammoun.raven.borders.BorderModule;
import com.kammoun.raven.borders.config.BorderConfig;
import com.kammoun.raven.borders.utils.GridCell;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class BorderManagerCommand extends KCommand {
    private final BorderModule module;

    public BorderManagerCommand(BorderModule module) {
        super("bordermanager", "raven.borders.admin", false);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sendInfo(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "view" -> sendView(sender);
            case "tp" -> teleport(sender, args);
            case "setcell" -> setCell(sender, args);
            case "setorigin" -> setOrigin(sender, args);
            case "reload" -> {
                module.reload();
                send(sender, "&#33FF66Border config reloaded.");
            }
            default -> sendUsage(sender);
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("info", "view", "tp", "setcell", "setorigin", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            return filter(List.of("center", "north", "south", "east", "west"), args[1]);
        }
        return List.of();
    }

    private void sendInfo(CommandSender sender) {
        BorderConfig config = module.getBorderConfig();
        send(sender, "&#FF2B2B&lRaven Borders");
        send(sender, "&#7A7A7ACluster: &#FFFFFF" + config.cluster()
                + " &#7A7A7ACell: &#FFFFFF" + config.cellX() + "," + config.cellZ());
        send(sender, "&#7A7A7ASize: &#FFFFFF" + config.cellSize()
                + " &#7A7A7AOrigin: &#FFFFFF" + config.originX() + "," + config.originZ()
                + " &#7A7A7ANether ratio: &#FFFFFF" + config.netherRatio());
        World world = sender instanceof Player player ? player.getWorld() : firstWorld(World.Environment.NORMAL);
        if (world != null) {
            GridCell cell = config.cellFor(world.getEnvironment());
            if (cell != null) {
                send(sender, "&#7A7A7A" + world.getEnvironment().name()
                        + ": &#FFFFFFX " + cell.minX() + " -> " + (cell.maxX() - 1)
                        + " &#7A7A7AZ &#FFFFFF" + cell.minZ() + " -> " + (cell.maxZ() - 1));
            }
        }
        send(sender, "&#FFD447/bordermanager tp <center|north|south|east|west>");
        send(sender, "&#FFD447/bordermanager view");
        send(sender, "&#FFD447/bordermanager setcell <x> <z>");
        send(sender, "&#FFD447/bordermanager setorigin <x> <z>");
    }

    private void sendView(CommandSender sender) {
        BorderConfig config = module.getBorderConfig();
        send(sender, "&#FF2B2B&lBorder Map &#7A7A7A(" + config.cluster() + ")");
        for (int z = config.cellZ() - 1; z <= config.cellZ() + 1; z++) {
            StringBuilder line = new StringBuilder("&#7A7A7A");
            for (int x = config.cellX() - 1; x <= config.cellX() + 1; x++) {
                boolean current = x == config.cellX() && z == config.cellZ();
                if (current) {
                    line.append("&#33FF66[YOU] ");
                } else {
                    line.append("&#7A7A7A").append(x).append(",").append(z).append(" ");
                }
            }
            send(sender, line.toString());
        }
        World world = sender instanceof Player player ? player.getWorld() : firstWorld(World.Environment.NORMAL);
        if (world == null) return;
        GridCell cell = config.cellFor(world.getEnvironment());
        if (cell != null) {
            send(sender, "&#7A7A7AOwns X &#FFFFFF" + cell.minX() + " -> " + (cell.maxX() - 1)
                    + " &#7A7A7AZ &#FFFFFF" + cell.minZ() + " -> " + (cell.maxZ() - 1));
        }
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "&#D90429Only players can teleport to border positions.");
            return;
        }
        if (args.length < 2) {
            send(sender, "&#FFD447Usage: &#FFFFFF/bordermanager tp <center|north|south|east|west>");
            return;
        }
        BorderConfig config = module.getBorderConfig();
        GridCell cell = config.cellFor(player.getWorld().getEnvironment());
        if (cell == null) {
            send(sender, "&#D90429This dimension is not managed by Raven borders.");
            return;
        }

        double x = (cell.minX() + cell.maxX()) / 2.0D;
        double z = (cell.minZ() + cell.maxZ()) / 2.0D;
        double inset = 2.5D;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "north" -> z = cell.minZ() + inset;
            case "south" -> z = cell.maxZ() - inset;
            case "east" -> x = cell.maxX() - inset;
            case "west" -> x = cell.minX() + inset;
            case "center" -> { }
            default -> {
                send(sender, "&#D90429Unknown border position.");
                return;
            }
        }

        World world = player.getWorld();
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        double targetX = x;
        double targetZ = z;
        String position = args[1].toLowerCase(Locale.ROOT);
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        world.getChunkAtAsync(blockX >> 4, blockZ >> 4, true).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                SchedulerUtil.execute(player, () -> send(player, "&#D90429Target chunk failed to load."));
                return;
            }
            SchedulerUtil.execute(new Location(world, targetX, world.getMinHeight(), targetZ), () -> {
                int y = world.getHighestBlockAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY() + 1;
                Location location = new Location(world, targetX, y, targetZ, yaw, pitch);
                player.teleportAsync(location).thenAccept(success -> SchedulerUtil.execute(player, () -> {
                    if (Boolean.TRUE.equals(success)) {
                        send(player, "&#33FF66Teleported to border &#FFFFFF" + position + "&#33FF66.");
                    } else {
                        send(player, "&#D90429Teleport failed.");
                    }
                }));
            });
        });
    }

    private void setCell(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "&#FFD447Usage: &#FFFFFF/bordermanager setcell <x> <z>");
            return;
        }
        Integer x = parseInt(args[1]);
        Integer z = parseInt(args[2]);
        if (x == null || z == null) {
            send(sender, "&#D90429Cell coordinates must be whole numbers.");
            return;
        }
        module.getApi().getConfig().set("grid.cell-x", x);
        module.getApi().getConfig().set("grid.cell-z", z);
        module.getApi().saveConfig();
        module.reload();
        send(sender, "&#33FF66Border cell updated to &#FFFFFF" + x + "," + z + "&#33FF66.");
    }

    private void setOrigin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "&#FFD447Usage: &#FFFFFF/bordermanager setorigin <x> <z>");
            return;
        }
        Long x = parseLong(args[1]);
        Long z = parseLong(args[2]);
        if (x == null || z == null) {
            send(sender, "&#D90429Origin coordinates must be whole numbers.");
            return;
        }
        module.getApi().getConfig().set("grid.origin-x", x);
        module.getApi().getConfig().set("grid.origin-z", z);
        module.getApi().saveConfig();
        module.reload();
        send(sender, "&#33FF66Border origin updated to &#FFFFFF" + x + "," + z + "&#33FF66.");
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&#FFD447/bordermanager info");
        send(sender, "&#FFD447/bordermanager view");
        send(sender, "&#FFD447/bordermanager tp <center|north|south|east|west>");
        send(sender, "&#FFD447/bordermanager setcell <x> <z>");
        send(sender, "&#FFD447/bordermanager setorigin <x> <z>");
        send(sender, "&#FFD447/bordermanager reload");
    }

    private World firstWorld(World.Environment environment) {
        return Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == environment)
                .findFirst()
                .orElse(null);
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }

    private Integer parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatFormatter.color(message));
    }
}
