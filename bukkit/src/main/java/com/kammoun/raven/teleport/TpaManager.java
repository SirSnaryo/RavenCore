package com.kammoun.raven.teleport;

import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.redis.event.TpaNotificationEvent;
import com.kammoun.raven.library.redis.event.TpaRequestEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.teleport.config.TeleportConfig;
import com.kammoun.raven.teleport.config.TeleportMessage;
import com.kammoun.raven.teleport.model.TeleportSettings;
import com.kammoun.raven.teleport.model.TpaRequest;
import com.kammoun.raven.teleport.settings.TeleportSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TpaManager {

    private final TeleportModule module;
    private final TeleportConfig config;
    private final TeleportSettingsService settingsService;
    private final Map<UUID, Map<UUID, TpaRequest>> activeRequests = new ConcurrentHashMap<>();
    private final Map<UUID, SchedulerUtil.Task> countdownTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();

    public TpaManager(TeleportModule module, TeleportConfig config, TeleportSettingsService settingsService) {
        this.module = module;
        this.config = config;
        this.settingsService = settingsService;
    }

    public void request(Player requester, NetworkPlayerInfo target, TpaRequest.Type type) {
        if (target.getUuid().equals(requester.getUniqueId())) {
            config.send(requester, TeleportMessage.SELF_TELEPORT);
            config.sound(requester, "error");
            return;
        }

        long cooldownLeft = requestCooldownLeft(requester.getUniqueId());
        if (cooldownLeft > 0) {
            config.send(requester, TeleportMessage.REQUEST_COOLDOWN,
                    "%player%", target.getPlayerName(), "%time%", String.valueOf(cooldownLeft));
            config.actionBar(requester, TeleportMessage.REQUEST_COOLDOWN,
                    "%player%", target.getPlayerName(), "%time%", String.valueOf(cooldownLeft));
            config.sound(requester, "error");
            return;
        }

        TpaRequest request = new TpaRequest(
                requester.getUniqueId(),
                requester.getName(),
                module.currentServer(),
                module.currentServerDisplayName(),
                target.getUuid(),
                target.getPlayerName(),
                target.getServerId(),
                displayName(target),
                type
        );

        if (module.currentServer().equalsIgnoreCase(target.getServerId())) {
            if (receiveRequest(request)) {
                sent(requester, target.getPlayerName(), type);
            }
            return;
        }

        publishRequest(request);
        sent(requester, target.getPlayerName(), type);
    }

    public boolean receiveRequest(TpaRequest request) {
        Player target = Bukkit.getPlayer(request.targetId());
        if (target == null || !target.isOnline()) {
            return false;
        }

        TeleportSettings targetSettings = settingsService.get(target.getUniqueId());
        if (!targetSettings.accepts(request.type())) {
            notifyRequester(request, request.type() == TpaRequest.Type.TPA
                    ? TeleportMessage.BLOCK_TPA_REQUEST
                    : TeleportMessage.BLOCK_TPHERE_REQUEST);
            config.sound(target, "error");
            return false;
        }

        if (hasPendingRequest(request.targetId(), request.requesterId())) {
            notifyRequester(request, TeleportMessage.REQUEST_COOLDOWN);
            return false;
        }

        if (targetSettings.autoAccept()) {
            activeRequests.computeIfAbsent(request.targetId(), ignored -> new ConcurrentHashMap<>())
                    .put(request.requesterId(), request);
            acceptRequest(target, request);
            return false;
        }

        activeRequests.computeIfAbsent(request.targetId(), ignored -> new ConcurrentHashMap<>())
                .put(request.requesterId(), request);
        notifyTarget(target, request);
        return true;
    }

    public Optional<TpaRequest> latestRequest(UUID targetId) {
        return requestsFor(targetId).values().stream()
                .filter(request -> !request.isExpired(config.clearTimeSeconds()))
                .max(Comparator.comparingLong(TpaRequest::createdAt));
    }

    public Optional<TpaRequest> requestFrom(UUID targetId, UUID requesterId) {
        TpaRequest request = requestsFor(targetId).get(requesterId);
        if (request == null || request.isExpired(config.clearTimeSeconds())) {
            removeRequest(targetId, requesterId);
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public Map<UUID, TpaRequest> requestsFor(UUID targetId) {
        return activeRequests.getOrDefault(targetId, Map.of());
    }

    public boolean hasPendingRequest(UUID targetId, UUID requesterId) {
        return requestFrom(targetId, requesterId).isPresent();
    }

    public void acceptRequest(Player target, TpaRequest request) {
        removeRequest(request.targetId(), request.requesterId());

        NetworkPlayerInfo requester = new NetworkPlayerInfo(
                request.requesterId(),
                request.requesterName(),
                request.requesterServer(),
                request.requesterServerDisplayName(),
                null
        );
        NetworkPlayerInfo destination = new NetworkPlayerInfo(
                request.targetId(),
                request.targetName(),
                request.targetServer(),
                request.targetServerDisplayName(),
                target.getWorld().getName()
        );

        config.send(target, TeleportMessage.ACCEPTED_TELEPORT);
        config.actionBar(target, TeleportMessage.ACCEPTED_TELEPORT);
        notifyRequester(request, TeleportMessage.TARGET_ACCEPTED_TELEPORT);

        if (request.type() == TpaRequest.Type.TPA) {
            Player localRequester = Bukkit.getPlayer(request.requesterId());
            if (localRequester != null) {
                startCountdown(localRequester, () -> module.teleportToPlayer(Bukkit.getConsoleSender(), requester, destination));
            } else {
                module.teleportToPlayer(Bukkit.getConsoleSender(), requester, destination);
            }
            return;
        }

        NetworkPlayerInfo targetInfo = new NetworkPlayerInfo(
                target.getUniqueId(),
                target.getName(),
                module.currentServer(),
                module.currentServerDisplayName(),
                target.getWorld().getName()
        );
        startCountdown(target, () -> module.teleportToPlayer(Bukkit.getConsoleSender(), targetInfo, requester));
    }

    public void denyRequest(Player target, TpaRequest request) {
        removeRequest(request.targetId(), request.requesterId());
        config.send(target, TeleportMessage.CANCELLED_REQUEST, "%player%", request.requesterName());
        config.sound(target, "request-cancelled");
        notifyRequester(request, TeleportMessage.CANCELLED_REQUEST_SENDER);
    }

    public boolean cancelOutgoing(UUID requesterId) {
        boolean removed = false;
        for (Map<UUID, TpaRequest> requests : activeRequests.values()) {
            removed |= requests.remove(requesterId) != null;
        }
        activeRequests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return removed;
    }

    public void cleanup() {
        long clearTime = config.clearTimeSeconds();
        activeRequests.values().forEach(map -> map.entrySet().removeIf(entry -> entry.getValue().isExpired(clearTime)));
        activeRequests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void unload(UUID playerId) {
        cancelTeleport(playerId);
        requestCooldowns.remove(playerId);
        activeRequests.remove(playerId);
        cancelOutgoing(playerId);
    }

    public void cancelTeleport(UUID playerId) {
        SchedulerUtil.Task task = countdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!countdownTasks.containsKey(player.getUniqueId())) {
            return;
        }

        if (event.getTo() != null
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        cancelTeleport(player.getUniqueId());
        config.send(player, TeleportMessage.TELEPORT_CANCELLED);
        config.actionBar(player, TeleportMessage.TELEPORT_MOVEMENT_CANCELLATION_ACTION_BAR);
        config.sound(player, "error");
    }

    public List<String> requesterNames(Player target, String prefix) {
        String lower = prefix.toLowerCase();
        return requestsFor(target.getUniqueId()).values().stream()
                .map(TpaRequest::requesterName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private void notifyTarget(Player target, TpaRequest request) {
        TeleportMessage message = request.type() == TpaRequest.Type.TPA
                ? TeleportMessage.RECEIVE_REQUEST
                : TeleportMessage.RECEIVE_HERE_REQUEST;
        TeleportMessage action = request.type() == TpaRequest.Type.TPA
                ? TeleportMessage.TPA_REQUEST_TARGET_ACTIONBAR
                : TeleportMessage.TPA_HERE_REQUEST_TARGET_ACTIONBAR;

        config.rich(target, message, "%player%", request.requesterName());
        config.actionBar(target, action, "%player%", request.requesterName());
        config.sound(target, "information-click");
    }

    private void sent(Player requester, String targetName, TpaRequest.Type type) {
        requestCooldowns.put(requester.getUniqueId(), System.currentTimeMillis());
        config.send(requester, type == TpaRequest.Type.TPA ? TeleportMessage.SENT_REQUEST : TeleportMessage.SENT_HERE_REQUEST,
                "%player%", targetName);
        config.actionBar(requester, type == TpaRequest.Type.TPA
                        ? TeleportMessage.TPA_REQUEST_SENDER_ACTIONBAR
                        : TeleportMessage.TPA_HERE_REQUEST_SENDER_ACTIONBAR,
                "%player%", targetName);
        config.sound(requester, "information-click");
    }

    private void notifyRequester(TpaRequest request, TeleportMessage message) {
        Player requester = Bukkit.getPlayer(request.requesterId());
        if (requester != null) {
            config.send(requester, message, "%player%", request.targetName());
            config.actionBar(requester, message, "%player%", request.targetName());
            config.sound(requester, soundFor(message));
            return;
        }

        TpaNotificationEvent event = new TpaNotificationEvent();
        event.setRecipientUuid(request.requesterId().toString());
        event.setMessageKey(message.name());
        event.setActionBarKey(message.name());
        event.setSoundKey(soundFor(message));
        event.setPlayerName(request.targetName());
        module.getApi().getNetworkEventBus().publish(event);
    }

    private void publishRequest(TpaRequest request) {
        TpaRequestEvent event = new TpaRequestEvent();
        event.setRequesterUuid(request.requesterId().toString());
        event.setRequesterName(request.requesterName());
        event.setRequesterServer(request.requesterServer());
        event.setRequesterServerDisplayName(request.requesterServerDisplayName());
        event.setTargetUuid(request.targetId().toString());
        event.setTargetName(request.targetName());
        event.setTargetServer(request.targetServer());
        event.setTargetServerDisplayName(request.targetServerDisplayName());
        event.setType(request.type().name());
        module.getApi().getNetworkEventBus().publish(event);
    }

    private long requestCooldownLeft(UUID requesterId) {
        long cooldownSeconds = config.requestCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return 0;
        }
        Long last = requestCooldowns.get(requesterId);
        if (last == null) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        long left = cooldownSeconds - elapsed;
        if (left <= 0) {
            requestCooldowns.remove(requesterId);
            return 0;
        }
        return left;
    }

    private String displayName(NetworkPlayerInfo info) {
        return info.getServerDisplayName() == null || info.getServerDisplayName().isBlank()
                ? info.getServerId()
                : info.getServerDisplayName();
    }

    private String soundFor(TeleportMessage message) {
        return switch (message) {
            case TARGET_ACCEPTED_TELEPORT -> "accept-teleport";
            case CANCELLED_REQUEST_SENDER -> "request-cancelled";
            default -> "error";
        };
    }

    private void removeRequest(UUID targetId, UUID requesterId) {
        Map<UUID, TpaRequest> requests = activeRequests.get(targetId);
        if (requests == null) return;
        requests.remove(requesterId);
        if (requests.isEmpty()) {
            activeRequests.remove(targetId);
        }
    }

    private void startCountdown(Player player, Runnable teleport) {
        int seconds = config.countdownSeconds();
        if (seconds <= 0) {
            teleport.run();
            return;
        }

        cancelTeleport(player.getUniqueId());
        AtomicInteger remaining = new AtomicInteger(seconds);
        SchedulerUtil.Task[] holder = new SchedulerUtil.Task[1];
        holder[0] = SchedulerUtil.runTimer(() -> SchedulerUtil.execute(player, () -> {
            if (!player.isOnline()) {
                cancelCountdown(player.getUniqueId(), holder[0]);
                return;
            }

            int left = remaining.getAndDecrement();
            if (left <= 0) {
                cancelCountdown(player.getUniqueId(), holder[0]);
                teleport.run();
                return;
            }

            String time = String.valueOf(left);
            config.send(player, TeleportMessage.TELEPORT_COUNTDOWN, "%time%", time);
            config.actionBar(player, TeleportMessage.TELEPORT_COUNTDOWN_ACTIONBAR, "%time%", time);
            config.sound(player, "count-down");
        }), 1L, 20L);
        countdownTasks.put(player.getUniqueId(), holder[0]);
    }

    private void cancelCountdown(UUID playerId, SchedulerUtil.Task task) {
        countdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
