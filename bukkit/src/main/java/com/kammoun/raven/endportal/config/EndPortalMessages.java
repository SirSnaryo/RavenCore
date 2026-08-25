package com.kammoun.raven.endportal.config;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.config.KMessageLoader;

public final class EndPortalMessages extends KMessageLoader<EndPortalMessage> {
    public EndPortalMessages(RavenCore plugin) {
        super(plugin, "endportal/messages.yml", EndPortalMessage.class);
    }
}
