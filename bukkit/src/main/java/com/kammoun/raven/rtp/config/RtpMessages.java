package com.kammoun.raven.rtp.config;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.config.KMessageLoader;

public final class RtpMessages extends KMessageLoader<RtpMessage> {
    public RtpMessages(RavenCore plugin) {
        super(plugin, "rtp/messages.yml", RtpMessage.class);
    }
}
