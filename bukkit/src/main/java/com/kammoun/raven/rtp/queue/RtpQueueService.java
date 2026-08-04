package com.kammoun.raven.rtp.queue;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.locationpool.RtpLocation;
import com.kammoun.raven.library.network.NetworkPlayerInfo;
import com.kammoun.raven.library.redis.event.RtpQueueNotificationEvent;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import com.kammoun.raven.rtp.RtpModule;
import com.kammoun.raven.rtp.RtpTeleportService;
import com.kammoun.raven.rtp.config.RtpConfig;
import com.kammoun.raven.rtp.config.RtpMessage;
import com.kammoun.raven.rtp.config.RtpMessages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.Optional;
import java.util.UUID;

public final class RtpQueueService {

    private static final String LIST_KEY = "raven:rtpqueue:list";
    private static final String HASH_KEY = "raven:rtpqueue:entries";
    private static final String SET_KEY = "raven:rtpqueue:queued";
    private static final String LOCK_KEY = "raven:rtpqueue:lock";
    private static final String NETWORK_PLAYERS_KEY = "raven:network:players";

    private final RavenCore plugin;
    private final RtpModule module;
    private final RtpConfig config;
    private final RtpMessages messages;
    private final RtpTeleportService teleportService;
    private final JedisPool pool;
    private final Gson gson = new Gson();
    private SchedulerUtil.Task matcherTask;

    public RtpQueueService(RavenCore plugin, RtpModule module, RtpConfig config,
                           RtpMessages messages, RtpTeleportService teleportService) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
        this.messages = messages;
        this.teleportService = teleportService;
        this.pool = plugin.getRedisMessenger().getConnection().getPool();
    }

    public void start() {
        long interval = Math.max(20L, config.queueMatchIntervalTicks());
        matcherTask = SchedulerUtil.runAsyncTimer(this::tryMatch, interval, interval);
    }

    public void stop() {
        if (matcherTask != null) {
            matcherTask.cancel();
            matcherTask = null;
        }
    }

    public void toggle(Player player) {
        if (!config.queueEnabled()) {
            messages.send(player, RtpMessage.QUEUE_DISABLED);
            return;
        }
        if (plugin.getCombatModule() != null && !plugin.getCombatModule().canTransfer(player, true)) {
            return;
        }
        if (teleportService.isBusy(player.getUniqueId())) {
            messages.send(player, RtpMessage.ALREADY_TELEPORTING);
            return;
        }
        long remaining = teleportService.remainingCooldown(player.getUniqueId());
        if (remaining > 0) {
            messages.send(player, RtpMessage.COOLDOWN, "%seconds%", String.valueOf(remaining));
            return;
        }

        NetworkPlayerInfo info = new NetworkPlayerInfo(player.getUniqueId(), player.getName(),
                plugin.getServerId(), plugin.getServerDisplayName(), player.getWorld().getName());
        SchedulerUtil.runAsync(() -> toggleAsync(player.getUniqueId(), RtpQueueEntry.of(info)));
    }

    public void remove(Player player) {
        SchedulerUtil.runAsync(() -> remove(player.getUniqueId()));
    }

    public void deliver(RtpQueueNotificationEvent event) {
        if (event == null || event.getRecipientUuid() == null || event.getMessageKey() == null) {
            return;
        }
        RtpMessage message;
        UUID uuid;
        try {
            uuid = UUID.fromString(event.getRecipientUuid());
            message = RtpMessage.valueOf(event.getMessageKey());
        } catch (RuntimeException ignored) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        SchedulerUtil.execute(player, () -> {
            messages.send(player, message,
                    "%player%", safe(event.getPartnerName()),
                    "%server%", safe(event.getTargetServer()));
            module.getSounds().play(player, soundKey(message));
        });
    }

    private void toggleAsync(UUID uuid, RtpQueueEntry entry) {
        try (Jedis jedis = pool.getResource()) {
            if (jedis.sismember(SET_KEY, uuid.toString())) {
                remove(jedis, uuid);
                notify(entry, RtpMessage.QUEUE_LEFT, null, null);
                return;
            }

            jedis.hset(HASH_KEY, uuid.toString(), gson.toJson(entry));
            jedis.sadd(SET_KEY, uuid.toString());
            jedis.rpush(LIST_KEY, uuid.toString());
            notify(entry, RtpMessage.QUEUE_JOINED, null, null);
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP Queue] Failed to toggle queue for "
                    + entry.name() + ": " + e.getMessage());
            notify(entry, RtpMessage.QUEUE_FAILED, null, null);
            return;
        }
        tryMatch();
    }

    private void tryMatch() {
        String token = UUID.randomUUID().toString();
        try (Jedis jedis = pool.getResource()) {
            String lock = jedis.set(LOCK_KEY, token, SetParams.setParams()
                    .nx().px(Math.max(1000L, config.queueLockMillis())));
            if (!"OK".equals(lock)) return;
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP Queue] Failed to acquire match lock: " + e.getMessage());
            return;
        }

        try {
            matchLocked();
        } finally {
            releaseLock(token);
        }
    }

    private void matchLocked() {
        RtpQueueEntry first;
        RtpQueueEntry second;
        try (Jedis jedis = pool.getResource()) {
            first = nextValid(jedis);
            second = nextValid(jedis);
            if (first == null) return;
            if (second == null) {
                jedis.rpush(LIST_KEY, first.uuid().toString());
                return;
            }
            remove(jedis, first.uuid());
            remove(jedis, second.uuid());
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP Queue] Failed to read match candidates: " + e.getMessage());
            return;
        }

        Optional<RtpLocation> random = teleportService.randomOverworldLocation();
        if (random.isEmpty()) {
            notify(first, RtpMessage.NO_LOCATIONS, second.name(), null);
            notify(second, RtpMessage.NO_LOCATIONS, first.name(), null);
            return;
        }

        RtpPairTarget pair = RtpPairTarget.from(random.get(), config.queueDistanceBlocks());
        boolean firstSent = teleportService.teleportTo(first.uuid(), pair.first());
        boolean secondSent = teleportService.teleportTo(second.uuid(), pair.second());
        if (!firstSent || !secondSent) {
            notify(first, RtpMessage.QUEUE_FAILED, second.name(), pair.first().serverId());
            notify(second, RtpMessage.QUEUE_FAILED, first.name(), pair.second().serverId());
            return;
        }

        teleportService.startCooldown(first.uuid());
        teleportService.startCooldown(second.uuid());
        notify(first, RtpMessage.QUEUE_MATCHED, second.name(), pair.first().serverId());
        notify(second, RtpMessage.QUEUE_MATCHED, first.name(), pair.second().serverId());
    }

    private RtpQueueEntry nextValid(Jedis jedis) {
        long ttlMillis = Math.max(10L, config.queueEntryTtlSeconds()) * 1000L;
        for (int i = 0; i < 64; i++) {
            String rawUuid = jedis.lpop(LIST_KEY);
            if (rawUuid == null) return null;
            if (!jedis.sismember(SET_KEY, rawUuid)) continue;

            RtpQueueEntry entry = read(jedis.hget(HASH_KEY, rawUuid));
            if (entry == null || entry.expired(ttlMillis) || !jedis.hexists(NETWORK_PLAYERS_KEY, rawUuid)) {
                remove(jedis, rawUuid);
                continue;
            }
            return entry;
        }
        return null;
    }

    private RtpQueueEntry read(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return gson.fromJson(json, RtpQueueEntry.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private void remove(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            remove(jedis, uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP Queue] Failed to remove " + uuid + ": " + e.getMessage());
        }
    }

    private void remove(Jedis jedis, UUID uuid) {
        remove(jedis, uuid.toString());
    }

    private void remove(Jedis jedis, String uuid) {
        jedis.srem(SET_KEY, uuid);
        jedis.hdel(HASH_KEY, uuid);
        jedis.lrem(LIST_KEY, 0, uuid);
    }

    private void releaseLock(String token) {
        try (Jedis jedis = pool.getResource()) {
            if (token.equals(jedis.get(LOCK_KEY))) {
                jedis.del(LOCK_KEY);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[RTP Queue] Failed to release match lock: " + e.getMessage());
        }
    }

    private void notify(RtpQueueEntry entry, RtpMessage message, String partnerName, String targetServer) {
        Player player = Bukkit.getPlayer(entry.uuid());
        if (player != null) {
            SchedulerUtil.execute(player, () -> {
                messages.send(player, message,
                        "%player%", safe(partnerName),
                        "%server%", safe(targetServer));
                module.getSounds().play(player, soundKey(message));
            });
            return;
        }

        RtpQueueNotificationEvent event = new RtpQueueNotificationEvent();
        event.setRecipientUuid(entry.uuid().toString());
        event.setMessageKey(message.name());
        event.setPartnerName(partnerName);
        event.setTargetServer(targetServer);
        plugin.getNetworkEventBus().publish(event);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String soundKey(RtpMessage message) {
        return switch (message) {
            case QUEUE_FAILED, NO_LOCATIONS -> "warmup-cancelled";
            default -> "warmup-start";
        };
    }
}
