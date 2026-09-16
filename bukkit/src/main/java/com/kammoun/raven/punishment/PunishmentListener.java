package com.kammoun.raven.punishment;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.punishment.model.PunishmentRecord;
import com.kammoun.raven.punishment.model.PunishmentType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;

public final class PunishmentListener implements Listener {

    private final PunishmentService service;
    private final Logger logger;

    public PunishmentListener(PunishmentService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            Optional<PunishmentRecord> ban = service.active(event.getUniqueId(), event.getName(), PunishmentType.BAN);
            ban.ifPresent(record -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    LegacyComponentSerializer.legacySection().deserialize(ChatFormatter.color(service.banMessage(record)))));
        } catch (SQLException error) {
            logger.warning("[Punishment] Failed to check ban for " + event.getName() + ": " + error.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;
        try {
            Optional<PunishmentRecord> mute = service.active(event.getPlayer().getUniqueId(), event.getPlayer().getName(), PunishmentType.MUTE);
            if (mute.isEmpty()) return;
            event.setCancelled(true);
            SchedulerUtil.execute(event.getPlayer(), () -> event.getPlayer()
                    .sendMessage(ChatFormatter.color(service.muteNotice(mute.get()))));
        } catch (SQLException error) {
            logger.warning("[Punishment] Failed to check mute for " + event.getPlayer().getName() + ": " + error.getMessage());
        }
    }
}
