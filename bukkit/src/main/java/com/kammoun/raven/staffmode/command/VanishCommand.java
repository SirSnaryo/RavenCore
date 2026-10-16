package com.kammoun.raven.staffmode.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.staffmode.StaffModeService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class VanishCommand extends KCommand {

    private final StaffModeService service;

    public VanishCommand(StaffModeService service) {
        super("vanish", "raven.vanish", true);
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
