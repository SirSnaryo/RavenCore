package com.kammoun.raven.library.command;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class UnavailableCommand extends KCommand {

    private final String message;

    public UnavailableCommand(String commandName, String permission, boolean playerOnly, String message) {
        super(commandName, permission, playerOnly);
        this.message = message;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatFormatter.color(message));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
