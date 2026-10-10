package com.kammoun.raven.staffmode.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.staffmode.StaffModeService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class StaffModeCommand extends KCommand {

    private final StaffModeService service;

    public StaffModeCommand(StaffModeService service) {
        super("staffmode", "raven.staffmode", true);
        this.service = service;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        service.toggleStaffMode((Player) sender);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
