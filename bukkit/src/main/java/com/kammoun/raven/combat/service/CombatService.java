package com.kammoun.raven.combat.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.combat.config.CombatConfig;
import com.kammoun.raven.combat.model.CombatTag;
import com.kammoun.raven.combat.store.CombatStore;
import com.kammoun.raven.library.redis.event.CombatTagEvent;
import com.kammoun.raven.library.redis.event.CombatUntagEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatService {
    private final RavenCore plugin;
    private final CombatConfig config;
    private final CombatStore store;
    private final Map<UUID, CombatTag> tags = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> actionBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUntags = new ConcurrentHashMap<>();

    public CombatService(RavenCore plugin, CombatConfig config, CombatStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
    }

    public boolean isTagged(UUID uuid) {
        CombatTag tag = tags.get(uuid);
        if (tag == null) return false;
        if (tag.active()) return true;
        remove(uuid, true, true);
        return false;
    }

    public boolean isTagged(Player player) {
        return isTagged(player.getUniqueId());
    }

    public int remaining(UUID uuid) {
        CombatTag tag = tags.get(uuid);
        return tag == null ? 0 : tag.remainingSeconds();
    }

    public Optional<UUID> opponent(UUID uuid) {
        CombatTag tag = tags.get(uuid);
        if (tag == null || !tag.active() || tag.opponentUuid() == null) {
            return Optional.empty();
        }
        return Optional.of(tag.opponentUuid());
    }

    public void tag(Player attacker, Player victim) {
        if (attacker.equals(victim)) return;
        long expiresAt = System.currentTimeMillis() + config.durationSeconds() * 1000L;
        apply(attacker, new CombatTag(victim.getUniqueId(), expiresAt), true);
        apply(victim, new CombatTag(attacker.getUniqueId(), expiresAt), true);
        publish(attacker.getUniqueId(), victim.getUniqueId(), expiresAt);
        publish(victim.getUniqueId(), attacker.getUniqueId(), expiresAt);
    }

    public void applyRemote(CombatTagEvent event) {
        try {
            UUID uuid = UUID.fromString(event.getPlayerUuid());
            if (isStaleTag(uuid, event.getTimestamp())) return;
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) return;
            UUID opponent = event.getOpponentUuid() == null ? null : UUID.fromString(event.getOpponentUuid());
            SchedulerUtil.execute(player, () -> apply(player, new CombatTag(opponent, event.getExpiresAt()), true));
        } catch (RuntimeException ignored) {
        }
    }

    public void removeRemote(CombatUntagEvent event) {
        try {
            UUID uuid = UUID.fromString(event.getPlayerUuid());
            rememberUntag(uuid, event.getTimestamp());
            remove(uuid, event.isNotify(), false);
        } catch (RuntimeException ignored) {
        }
    }

    public void load(Player player) {
        SchedulerUtil.runAsync(() -> {
            CombatTag tag = store.load(player.getUniqueId());
            if (tag != null) SchedulerUtil.execute(player, () -> {
                if (player.isOnline()) apply(player, tag, false);
            });
        });
    }

    public void remove(UUID uuid, boolean notify, boolean publish) {
        rememberUntag(uuid, System.currentTimeMillis());
        tags.remove(uuid);
        ScheduledTask task = actionBars.remove(uuid);
        if (task != null) task.cancel();
        SchedulerUtil.runAsync(() -> store.delete(uuid));
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) SchedulerUtil.execute(player, () -> {
            player.sendActionBar(net.kyori.adventure.text.Component.empty());
            if (notify) config.send(player, "leave-combat");
        });
        if (publish) {
            CombatUntagEvent event = new CombatUntagEvent();
            event.setPlayerUuid(uuid.toString());
            event.setNotify(notify);
            plugin.getNetworkEventBus().publish(event);
        }
    }

    public void unloadLocal(UUID uuid) {
        tags.remove(uuid);
        ScheduledTask task = actionBars.remove(uuid);
        if (task != null) task.cancel();
    }

    public void markNetworkTransfer(UUID uuid) {
        store.markNetworkTransfer(uuid, 15);
    }

    public boolean isNetworkTransfer(UUID uuid) {
        return store.isNetworkTransfer(uuid);
    }

    public void clearLocal() {
        actionBars.values().forEach(ScheduledTask::cancel);
        actionBars.clear();
        tags.clear();
        lastUntags.clear();
    }

    private void apply(Player player, CombatTag tag, boolean announce) {
        if (config.ignoreOp() && player.isOp()) return;
        if (player.isDead() || !player.isValid()) return;
        boolean fresh = !isTagged(player.getUniqueId());
        tags.put(player.getUniqueId(), tag);
        SchedulerUtil.runAsync(() -> {
            if (tags.get(player.getUniqueId()) == tag) store.save(player.getUniqueId(), tag);
        });
        if (fresh && announce) config.send(player, "enter-combat");
        startActionBar(player);
    }

    private boolean isStaleTag(UUID uuid, long eventTimestamp) {
        Long lastUntag = lastUntags.get(uuid);
        return lastUntag != null && eventTimestamp > 0L && eventTimestamp <= lastUntag;
    }

    private void rememberUntag(UUID uuid, long timestamp) {
        if (timestamp <= 0L) return;
        lastUntags.merge(uuid, timestamp, Math::max);
    }

    private void publish(UUID player, UUID opponent, long expiresAt) {
        CombatTagEvent event = new CombatTagEvent();
        event.setPlayerUuid(player.toString());
        event.setOpponentUuid(opponent == null ? null : opponent.toString());
        event.setExpiresAt(expiresAt);
        plugin.getNetworkEventBus().publish(event);
    }

    private void startActionBar(Player player) {
        if (!config.actionBarEnabled()) return;
        ScheduledTask previous = actionBars.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
            if (!player.isOnline() || !isTagged(player)) {
                scheduled.cancel();
                actionBars.remove(player.getUniqueId(), scheduled);
                return;
            }
            String text = config.actionBar().replace("%time%", String.valueOf(remaining(player.getUniqueId())));
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    com.kammoun.raven.library.platform.chat.ChatFormatter.color(text)));
        }, () -> actionBars.remove(player.getUniqueId()), 1L, 20L);
        actionBars.put(player.getUniqueId(), task);
    }
}
