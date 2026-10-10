package com.kammoun.raven.rtp.config;

import com.kammoun.raven.library.config.MessageKey;

public enum RtpMessage implements MessageKey {
    COOLDOWN("cooldown", "&7You must wait &#00c1fe%seconds%s &7before using RTP again!"),
    COOLDOWN_ACTIONBAR("cooldown-actionbar",
            "&7You must wait &#00c1fe%seconds%s &7before using RTP again!"),
    WARMUP_START("warmup-start", "&7Teleporting in &#00c1fe%seconds%&7... Don't move!"),
    WARMUP_ACTIONBAR("warmup-actionbar", "&7Teleporting in &#00c1fe%seconds%&7..."),
    CANCELLED_MOVE("warmup-cancelled-move", "&cTeleport cancelled - you moved."),
    CANCELLED_DAMAGE("warmup-cancelled-damage", "&cTeleport cancelled - you took damage."),
    NO_LOCATIONS("no-locations-available", "&cNo safe locations are available right now. Try again shortly."),
    ALREADY_TELEPORTING("already-teleporting", "&cA random teleport is already in progress."),
    TRANSFER_FAILED("transfer-failed", "&cThe destination server could not be reached. Please try again."),
    WORLD_MISSING("world-missing", "&cThe destination world is not loaded. Please contact an administrator."),
    QUEUE_DISABLED("queue.disabled", "&#D90429RTP Queue is currently disabled."),
    QUEUE_JOINED("queue.joined", "&#4CC9F0RTP Queue &#7A7A7AYou joined the queue. Waiting for another player..."),
    QUEUE_LEFT("queue.left", "&#4CC9F0RTP Queue &#7A7A7AYou left the queue."),
    QUEUE_MATCHED("queue.matched", "&#33FF66RTP Queue matched you with &#FFFFFF%player%&#33FF66. Teleporting..."),
    QUEUE_FAILED("queue.failed", "&#D90429RTP Queue failed to teleport you. Please try again."),
    END_DISABLED("end-disabled", "&#D90429Random teleport to the End is currently disabled.");

    private final String path;
    private final String[] defaults;

    RtpMessage(String path, String... defaults) {
        this.path = path;
        this.defaults = defaults;
    }

    @Override public String getPath() { return path; }
    @Override public String[] getDefault() { return defaults; }
}
