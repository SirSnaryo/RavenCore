package com.kammoun.raven.devmode.command;

import com.kammoun.raven.devmode.DevModeService;
import com.kammoun.raven.library.command.KCommand;
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
        service.toggle((Player) sender);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
