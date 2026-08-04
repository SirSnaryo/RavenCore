package com.kammoun.raven.rtp;

import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class RtpQueueCommand extends KCommand {

    private final RtpModule module;

    public RtpQueueCommand(RtpModule module) {
        super("rtpq", "raven.rtp.queue", true);
        this.module = module;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        module.getQueueService().toggle((Player) sender);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
