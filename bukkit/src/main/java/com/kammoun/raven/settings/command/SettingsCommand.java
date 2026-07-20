package com.kammoun.raven.settings.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.settings.SettingsModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SettingsCommand extends KCommand {

    private final SettingsModule module;

    public SettingsCommand(SettingsModule module) {
        super("settings", "raven.settings", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        module.getMenu().open((Player) sender);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
