package com.kammoun.raven.tpa.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class SpeedCommand extends KCommand {
    private static final double MIN_SPEED = 0.0D;
    private static final double MAX_SPEED = 10.0D;

    public SpeedCommand() {
        super("speed", "raven.speed", true);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length != 1) {
            send(player, "&#FFD447Usage: &#FFFFFF/speed <0-10>");
            return;
        }

        double speed;
        try {
            speed = Double.parseDouble(args[0]);
        } catch (NumberFormatException ignored) {
            send(player, "&#D90429Speed must be a number from &#FFFFFF0 &#D90429to &#FFFFFF10&#D90429.");
            return;
        }

        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            send(player, "&#D90429Speed must be between &#FFFFFF0 &#D90429and &#FFFFFF10&#D90429.");
            return;
        }

        float bukkitSpeed = (float) (speed / MAX_SPEED);
        if (player.isFlying()) {
            player.setFlySpeed(bukkitSpeed);
            send(player, "&#33FF66Fly speed set to &#FFFFFF" + trim(speed) + "&#33FF66.");
            return;
        }

        player.setWalkSpeed(bukkitSpeed);
        send(player, "&#33FF66Walk speed set to &#FFFFFF" + trim(speed) + "&#33FF66.");
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10").stream()
                .filter(value -> value.startsWith(args[0]))
                .toList();
    }

    private void send(Player player, String message) {
        player.sendMessage(ChatFormatter.color(message));
    }

    private String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
