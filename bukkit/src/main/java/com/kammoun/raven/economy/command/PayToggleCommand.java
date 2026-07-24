package com.kammoun.raven.economy.command;

import com.kammoun.raven.economy.config.EconomyMessages;
import com.kammoun.raven.economy.settings.EconomyPlayerSettings;
import com.kammoun.raven.economy.settings.EconomySettingsService;
import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PayToggleCommand extends KCommand {

    private final EconomySettingsService settings;
    private final EconomyMessages messages;

    public PayToggleCommand(EconomySettingsService settings, EconomyMessages messages) {
        super("paytoggle", null, true);
        this.settings = settings;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        EconomyPlayerSettings updated = settings.togglePayments(player.getUniqueId());
        player.sendMessage(messages.get(updated.acceptsPayments() ? "settings.paytoggle-on" : "settings.paytoggle-off"));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
