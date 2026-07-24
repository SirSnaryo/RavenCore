package com.kammoun.raven.chat.service;

import com.kammoun.raven.chat.config.ChatConfig;
import com.kammoun.raven.chat.model.AutoMessage;
import com.kammoun.raven.library.redis.event.ChatBroadcastEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.logging.Logger;

public class ChatAutoMessageService {

    private static final String AUTO_MESSAGE_INDEX_KEY = "raven:chat:auto:index";

    private final NetworkEventBus eventBus;
    private final JedisPool pool;
    private final String serverId;
    private final ChatFormatService formatter;
    private final Logger logger;

    private SchedulerUtil.Task task;
    private ChatConfig config;

    public ChatAutoMessageService(NetworkEventBus eventBus, JedisPool pool, String serverId,
                                  ChatFormatService formatter, Logger logger) {
        this.eventBus = eventBus;
        this.pool = pool;
        this.serverId = serverId;
        this.formatter = formatter;
        this.logger = logger;
    }

    public void start(ChatConfig config) {
        stop();
        this.config = config;
        if (!config.autoMessagesEnabled() || config.autoMessages().isEmpty()) {
            return;
        }
        task = SchedulerUtil.runAsyncTimer(this::publishNext, config.autoMessageIntervalTicks(),
                config.autoMessageIntervalTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void publishNext() {
        ChatConfig snapshot = config;
        if (pool == null || snapshot == null || snapshot.autoMessages().isEmpty()) {
            return;
        }

        try (Jedis jedis = pool.getResource()) {
            String ok = jedis.set(snapshot.autoMessageLockKey(), serverId,
                    SetParams.setParams().nx().ex(snapshot.autoMessageLockTtlSeconds()));
            if (!"OK".equals(ok)) {
                return;
            }

            AutoMessage autoMessage = nextMessage(jedis, snapshot.autoMessages());
            ChatBroadcastEvent event = new ChatBroadcastEvent();
            event.setMessage(formatter.serialize(formatter.deserializeConfigured(autoMessage.message())));
            event.setActionBar(formatter.serialize(formatter.deserializeConfigured(autoMessage.actionBar())));
            event.setSoundKey(autoMessage.soundKey());
            eventBus.publish(event);
        } catch (Exception e) {
            logger.warning("[Chat] Failed to publish auto message: " + e.getMessage());
        }
    }

    private AutoMessage nextMessage(Jedis jedis, List<AutoMessage> messages) {
        long index = jedis.incr(AUTO_MESSAGE_INDEX_KEY) - 1L;
        return messages.get((int) (index % messages.size()));
    }
}
