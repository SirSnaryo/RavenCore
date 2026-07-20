package com.kammoun.raven.chat.settings;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.chat.model.ChatSettings;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChatSettingsService {

    private static final String KEY_PREFIX = "raven:chat:settings:";
    private static final String CHAT_FIELD = "chat";
    private static final String BROADCAST_FIELD = "broadcast";

    private final JedisPool pool;
    private final Logger logger;
    private final Map<UUID, ChatSettings> cache = new ConcurrentHashMap<>();

    public ChatSettingsService(RavenCore plugin, Logger logger) {
        this.pool = plugin.getRedisMessenger().getConnection().getPool();
        this.logger = logger;
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        cache.putIfAbsent(uuid, ChatSettings.defaults(uuid));
        if (pool == null) {
            return;
        }

        SchedulerUtil.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                Map<String, String> values = jedis.hgetAll(key(uuid));
                cache.put(uuid, fromRedis(uuid, values));
            } catch (Exception e) {
                logger.warning("[Chat] Failed to load chat settings for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void unload(Player player) {
        cache.remove(player.getUniqueId());
    }

    public ChatSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, ChatSettings::defaults);
    }

    public boolean canReceiveChat(UUID uuid) {
        return get(uuid).chatEnabled();
    }

    public boolean canReceiveBroadcast(UUID uuid) {
        return get(uuid).broadcastEnabled();
    }

    public ChatSettings toggleChat(Player player) {
        ChatSettings next = get(player.getUniqueId()).withChat(!canReceiveChat(player.getUniqueId()));
        set(next);
        return next;
    }

    public ChatSettings toggleBroadcast(Player player) {
        ChatSettings next = get(player.getUniqueId()).withBroadcast(!canReceiveBroadcast(player.getUniqueId()));
        set(next);
        return next;
    }

    public ChatSettings setChat(Player player, boolean enabled) {
        ChatSettings next = get(player.getUniqueId()).withChat(enabled);
        set(next);
        return next;
    }

    public ChatSettings setBroadcast(Player player, boolean enabled) {
        ChatSettings next = get(player.getUniqueId()).withBroadcast(enabled);
        set(next);
        return next;
    }

    private void set(ChatSettings settings) {
        cache.put(settings.uuid(), settings);
        save(settings);
    }

    private void save(ChatSettings settings) {
        if (pool == null) {
            return;
        }

        SchedulerUtil.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                Map<String, String> values = new HashMap<>();
                values.put(CHAT_FIELD, Boolean.toString(settings.chatEnabled()));
                values.put(BROADCAST_FIELD, Boolean.toString(settings.broadcastEnabled()));
                jedis.hset(key(settings.uuid()), values);
            } catch (Exception e) {
                logger.warning("[Chat] Failed to save chat settings for " + settings.uuid() + ": " + e.getMessage());
            }
        });
    }

    private ChatSettings fromRedis(UUID uuid, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return ChatSettings.defaults(uuid);
        }
        return new ChatSettings(
                uuid,
                parse(values.get(CHAT_FIELD), true),
                parse(values.get(BROADCAST_FIELD), true)
        );
    }

    private boolean parse(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private String key(UUID uuid) {
        return KEY_PREFIX + uuid;
    }
}
