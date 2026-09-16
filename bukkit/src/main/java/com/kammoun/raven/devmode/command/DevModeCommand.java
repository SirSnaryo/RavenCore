package com.kammoun.raven.devmode.command;

import com.kammoun.raven.devmode.DevModeService;
import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DevModeCommand extends KCommand {

    private final DevModeService service;

    public DevModeCommand(DevModeService service) {
        super("devmode", "raven.devmode", true);
        this.service = service;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage(ChatFormatter.color("&#D90429DevMode requires operator access."));
            return;
        }
        service.toggle(player);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
