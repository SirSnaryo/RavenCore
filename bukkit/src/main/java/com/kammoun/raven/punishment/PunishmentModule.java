package com.kammoun.raven.punishment;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.punishment.command.PunishmentCommand;
import com.kammoun.raven.punishment.db.PunishmentRepository;
import lombok.Getter;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.List;

@Getter
public final class PunishmentModule extends KModule<RavenCore> {

    private PunishmentService service;

    public PunishmentModule() {
        super("punishment");
    }

    @Override
    public void onModuleEnable() {
        if (api.getMySqlManager() == null || !api.getMySqlManager().isConnected()) {
            registerUnavailableCommands();
            getLogger().warning("[Punishment] Disabled because MySQL is unavailable.");
            return;
        }

        PunishmentRepository repository = new PunishmentRepository(api.getMySqlManager());
        try {
            repository.createTable();
        } catch (SQLException error) {
            registerUnavailableCommands();
            getLogger().warning("[Punishment] Failed to create tables: " + error.getMessage());
            return;
        }

        service = new PunishmentService(repository, getConfig(), getLogger());
        registerCommands();
        registerEvent(new PunishmentListener(service, getLogger()));
        getLogger().info("[Punishment] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("[Punishment] Disabled.");
    }

    private void registerCommands() {
        registerCommand(new PunishmentCommand("ban", "raven.punishment.ban", service, PunishmentCommand.Mode.BAN), "ban");
        registerCommand(new PunishmentCommand("tempban", "raven.punishment.ban", service, PunishmentCommand.Mode.BAN), "tempban");
        registerCommand(new PunishmentCommand("mute", "raven.punishment.mute", service, PunishmentCommand.Mode.MUTE), "mute");
        registerCommand(new PunishmentCommand("tempmute", "raven.punishment.mute", service, PunishmentCommand.Mode.MUTE), "tempmute");
        registerCommand(new PunishmentCommand("unban", "raven.punishment.unban", service, PunishmentCommand.Mode.UNBAN), "unban");
        registerCommand(new PunishmentCommand("unmute", "raven.punishment.unmute", service, PunishmentCommand.Mode.UNMUTE), "unmute");
        registerCommand(new PunishmentCommand("kick", "raven.punishment.kick", service, PunishmentCommand.Mode.KICK), "kick");
        registerCommand(new PunishmentCommand("warn", "raven.punishment.warn", service, PunishmentCommand.Mode.WARN), "warn");
        registerCommand(new PunishmentCommand("history", "raven.punishment.history", service, PunishmentCommand.Mode.HISTORY), "history");
        registerCommand(new PunishmentCommand("punish", "raven.punishment.use", service, PunishmentCommand.Mode.PUNISH), "punish");
    }

    private void registerUnavailableCommands() {
        List.of("ban", "tempban", "mute", "tempmute", "unban", "unmute", "kick", "warn", "history", "punish").forEach(command ->
                registerCommand(new UnavailableCommand(command), command));
    }

    private static final class UnavailableCommand extends com.kammoun.raven.library.command.KCommand {

        private UnavailableCommand(String name) {
            super(name, null, false);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            sender.sendMessage(ChatFormatter.color("&#D90429Punishments are unavailable because MySQL is not connected."));
        }

        @Override
        public List<String> onTab(CommandSender sender, String[] args) {
            return List.of();
        }
    }
}
