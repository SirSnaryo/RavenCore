package com.kammoun.raven.billford.command;

import com.kammoun.raven.billford.manager.GuiManager;
import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class BillfordCommand extends KCommand {

    private final GuiManager guiManager;

    public BillfordCommand(GuiManager guiManager) {
        super("billford", "raven.billford", true);
        this.guiManager = guiManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        guiManager.openMainGui((Player) sender);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
