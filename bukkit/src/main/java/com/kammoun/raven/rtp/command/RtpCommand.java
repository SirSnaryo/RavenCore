package com.kammoun.raven.rtp.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.rtp.menu.DimensionMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class RtpCommand extends KCommand {

    private final DimensionMenu dimensionMenu;

    public RtpCommand(DimensionMenu dimensionMenu) {
        super("rtp", "raven.rtp", true);
        this.dimensionMenu = dimensionMenu;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        dimensionMenu.open((Player) sender); // playerOnly=true guarantees this
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
