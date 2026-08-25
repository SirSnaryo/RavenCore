package com.kammoun.raven.nightvision.command;

import com.kammoun.raven.library.command.KCommand;
import com.kammoun.raven.nightvision.NightVisionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class NightVisionCommand extends KCommand {

    private final NightVisionService service;
    private final String msgOn;
    private final String msgOff;

    public NightVisionCommand(NightVisionService service, String msgOn, String msgOff) {
        super("nv", "raven.nightvision", true);
        this.service = service;
        this.msgOn = msgOn;
        this.msgOff = msgOff;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        service.toggle(player);
        player.sendMessage(service.isEnabled(player) ? msgOn : msgOff);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
