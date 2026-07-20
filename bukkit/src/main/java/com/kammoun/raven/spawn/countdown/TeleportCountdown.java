package com.kammoun.raven.spawn.countdown;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.sound.KSound;
import com.kammoun.raven.spawn.config.SpawnMessages;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delays a teleport by a configurable number of seconds, showing an action-bar
 * countdown and cancelling if the player moves in the meantime. Shared by the
 * /spawn and /afk menus (both go through {@link com.kammoun.raven.spawn.menu.LocationMenu}).
 */
public class TeleportCountdown {

    private static final long TICKS_PER_SECOND = 20L;

    private final RavenCore api;
    private final SpawnMessages messages;
    private final int seconds;
    private final KSound tickSound;
    private final KSound deniedSound;

    private final Map<UUID, State> active = new ConcurrentHashMap<>();
    private ScheduledTask tickTask;

    public TeleportCountdown(RavenCore api, SpawnMessages messages, int seconds,
                             KSound tickSound, KSound deniedSound) {
        this.api = api;
        this.messages = messages;
        this.seconds = seconds;
        this.tickSound = tickSound;
        this.deniedSound = deniedSound;
    }

    public void start() {
        tickTask = api.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(api, task -> tick(), TICKS_PER_SECOND, TICKS_PER_SECOND);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        active.clear();
    }

    /**
     * Begins a countdown for the player, running {@code onComplete} once it finishes
     * uninterrupted. Replaces any countdown already in progress for that player.
     */
    public void begin(Player player, String destinationLabel, Runnable onComplete) {
        if (seconds <= 0) {
            onComplete.run();
            return;
        }
        State state = new State(destinationLabel, seconds, onComplete);
        active.put(player.getUniqueId(), state);
        sendProgress(player, state);
    }

    /**
     * Cancels any countdown of the player that moved (block position changed, not just
     * looked around) and notifies them. Wire this to {@link PlayerMoveEvent}.
     */
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (active.remove(event.getPlayer().getUniqueId()) != null) {
            playDenied(event.getPlayer());
            event.getPlayer().sendMessage(messages.get("teleport.cancelled"));
        }
    }

    private void tick() {
        if (active.isEmpty()) return;

        Iterator<Map.Entry<UUID, State>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            State state = entry.getValue();
            state.secondsLeft--;
            if (state.secondsLeft <= 0) {
                it.remove();
                state.onComplete.run();
            } else {
                sendProgress(player, state);
            }
        }
    }

    private void sendProgress(Player player, State state) {
        String text = messages.format("teleport.countdown",
                "seconds", state.secondsLeft, "server", state.destinationLabel);
        player.getScheduler().run(api, task -> {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
            tickSound.playSound(player);
        }, null);
    }

    public void playDenied(Player player) {
        player.getScheduler().run(api, task -> deniedSound.playSound(player), null);
    }

    private static class State {
        final String destinationLabel;
        int secondsLeft;
        final Runnable onComplete;

        State(String destinationLabel, int secondsLeft, Runnable onComplete) {
            this.destinationLabel = destinationLabel;
            this.secondsLeft = secondsLeft;
            this.onComplete = onComplete;
        }
    }
}
