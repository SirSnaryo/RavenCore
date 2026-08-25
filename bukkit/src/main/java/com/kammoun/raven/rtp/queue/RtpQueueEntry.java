package com.kammoun.raven.rtp.queue;

import com.kammoun.raven.library.network.NetworkPlayerInfo;

import java.util.UUID;

record RtpQueueEntry(UUID uuid, String name, String serverId, String serverDisplayName, long queuedAt) {

    static RtpQueueEntry of(NetworkPlayerInfo info) {
        return new RtpQueueEntry(info.getUuid(), info.getPlayerName(), info.getServerId(),
                info.getServerDisplayName(), System.currentTimeMillis());
    }

    boolean expired(long ttlMillis) {
        return ttlMillis > 0 && System.currentTimeMillis() - queuedAt > ttlMillis;
    }
}
