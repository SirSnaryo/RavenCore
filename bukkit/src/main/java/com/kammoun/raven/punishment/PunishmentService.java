package com.kammoun.raven.punishment;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.punishment.db.PunishmentRepository;
import com.kammoun.raven.punishment.model.PunishmentRecord;
import com.kammoun.raven.punishment.model.PunishmentType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class PunishmentService {

    private final PunishmentRepository repository;
    private final FileConfiguration config;
    private final Logger logger;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public PunishmentService(PunishmentRepository repository, FileConfiguration config, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.logger = logger;
    }

    public Optional<PunishmentRecord> active(UUID uuid, String name, PunishmentType type) throws SQLException {
        return repository.findActive(uuid, name, type);
    }

    public void punish(CommandSender actor, String targetName, PunishmentType type, Long durationMillis, String reason) {
        String actorName = actor.getName();
        Player online = Bukkit.getPlayerExact(targetName);
        UUID uuid = online == null ? null : online.getUniqueId();
        Long expiresAt = durationMillis == null ? null : System.currentTimeMillis() + durationMillis;
        SchedulerUtil.runAsync(() -> {
            try {
                PunishmentRecord record = repository.create(uuid, targetName, type, actorName, reason, expiresAt);
                tell(actor, message("messages.punished", "&#33FF66Punishment applied to &#FFD447%player%&#33FF66.")
                        .replace("%player%", targetName)
                        .replace("%type%", type.name().toLowerCase())
                        .replace("%reason%", reason));
                applyOnline(record);
            } catch (SQLException error) {
                logger.warning("[Punishment] Failed to punish " + targetName + ": " + error.getMessage());
                tell(actor, message("messages.error", "&#D90429Punishment action failed."));
            }
        });
    }

    public void pardon(CommandSender actor, String targetName, PunishmentType type) {
        String actorName = actor.getName();
        Player online = Bukkit.getPlayerExact(targetName);
        UUID uuid = online == null ? null : online.getUniqueId();
        SchedulerUtil.runAsync(() -> {
            try {
                boolean changed = repository.deactivate(uuid, targetName, type, actorName);
                tell(actor, changed
                        ? message("messages.pardoned", "&#33FF66Punishment removed from &#FFD447%player%&#33FF66.").replace("%player%", targetName)
                        : message("messages.not-punished", "&#D90429No active punishment found for &#FFD447%player%&#D90429.").replace("%player%", targetName));
            } catch (SQLException error) {
                logger.warning("[Punishment] Failed to pardon " + targetName + ": " + error.getMessage());
                tell(actor, message("messages.error", "&#D90429Punishment action failed."));
            }
        });
    }

    public void kick(CommandSender actor, Player target, String reason) {
        tell(actor, message("messages.kicked", "&#33FF66Kicked &#FFD447%player%&#33FF66.").replace("%player%", target.getName()));
        SchedulerUtil.execute(target, () -> target.kick(LegacyComponentSerializer.legacySection()
                .deserialize(ChatFormatter.color(kickMessage(reason)))));
    }

    public void history(CommandSender actor, String targetName) {
        SchedulerUtil.runAsync(() -> {
            try {
                List<PunishmentRecord> records = repository.history(targetName, 10);
                if (records.isEmpty()) {
                    tell(actor, message("messages.history-empty", "&#7A7A7ANo punishment history for &#FFD447%player%&#7A7A7A.")
                            .replace("%player%", targetName));
                    return;
                }
                tell(actor, message("messages.history-header", "&#FFD447Punishment history for &#FFFFFF%player%")
                        .replace("%player%", targetName));
                for (PunishmentRecord record : records) {
                    tell(actor, message("messages.history-line",
                            "&#7A7A7A#%id% &#D90429%type% &#FFFFFF%reason% &#7A7A7Aby &#FFD447%actor% &#7A7A7Aat &#4CC9F0%date%")
                            .replace("%id%", String.valueOf(record.id()))
                            .replace("%type%", record.type().name().toLowerCase())
                            .replace("%reason%", record.reason())
                            .replace("%actor%", record.actor())
                            .replace("%date%", dateFormat.format(Instant.ofEpochMilli(record.createdAt())))
                            .replace("%expires%", expires(record)));
                }
            } catch (SQLException error) {
                logger.warning("[Punishment] Failed to read history for " + targetName + ": " + error.getMessage());
                tell(actor, message("messages.error", "&#D90429Punishment action failed."));
            }
        });
    }

    private void applyOnline(PunishmentRecord record) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!matches(player, record)) continue;
            SchedulerUtil.execute(player, () -> {
                if (record.type() == PunishmentType.BAN) {
                    player.kick(LegacyComponentSerializer.legacySection()
                            .deserialize(ChatFormatter.color(banMessage(record))));
                } else if (record.type() == PunishmentType.MUTE) {
                    player.sendMessage(ChatFormatter.color(muteNotice(record)));
                } else {
                    player.sendMessage(ChatFormatter.color(message("messages.warn-notice",
                            "&#FF9F1CYou were warned. &#7A7A7AReason: &#FFFFFF%reason%")
                            .replace("%reason%", record.reason())
                            .replace("%actor%", record.actor())));
                }
            });
        }
    }

    private boolean matches(Player player, PunishmentRecord record) {
        return (record.targetUuid() != null && player.getUniqueId().equals(record.targetUuid()))
                || player.getName().equalsIgnoreCase(record.targetName());
    }

    public UUID knownUuid(String targetName) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    public String banMessage(PunishmentRecord record) {
        return message("messages.ban-screen", "&#D90429You are banned. &#7A7A7AReason: &#FFFFFF%reason%")
                .replace("%reason%", record.reason())
                .replace("%actor%", record.actor())
                .replace("%expires%", expires(record));
    }

    public String muteNotice(PunishmentRecord record) {
        return message("messages.mute-notice", "&#D90429You are muted. &#7A7A7AReason: &#FFFFFF%reason%")
                .replace("%reason%", record.reason())
                .replace("%actor%", record.actor())
                .replace("%expires%", expires(record));
    }

    private String kickMessage(String reason) {
        return message("messages.kick-screen", "&#D90429You were kicked. &#7A7A7AReason: &#FFFFFF%reason%")
                .replace("%reason%", reason);
    }

    private String expires(PunishmentRecord record) {
        if (record.permanent()) return "Permanent";
        long remaining = Math.max(0L, record.expiresAt() - System.currentTimeMillis()) / 1000L;
        long days = remaining / 86_400L;
        long hours = (remaining % 86_400L) / 3_600L;
        long minutes = (remaining % 3_600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1L, minutes) + "m";
    }

    private void tell(CommandSender sender, String message) {
        Runnable task = () -> sender.sendMessage(ChatFormatter.color(message));
        if (sender instanceof Player player) {
            SchedulerUtil.execute(player, task);
        } else {
            SchedulerUtil.run(task);
        }
    }

    private String message(String path, String fallback) {
        return config.getString(path, fallback).replace("<newline>", "\n");
    }
}
