package com.kammoun.raven.rtp;

import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class RtpCommand extends KCommand {
    private final RtpModule module;

    public RtpCommand(RtpModule module) {
        super("rtp", "raven.rtp", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        module.openDimension((Player) sender);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
