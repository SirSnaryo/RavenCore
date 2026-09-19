package com.kammoun.raven.placeholder;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;

/**
 * Unified %raven_*% PlaceholderAPI expansion for network, server, economy, stats, and metrics placeholders.
 */
public final class RavenPlaceholderExpansion extends KPlaceholderExpansion {

    public RavenPlaceholderExpansion(RavenCore plugin, RavenPlaceholders placeholders) {
        super(plugin, "raven");
        placeholders.keys().forEach(key -> register(key, player -> placeholders.value(player, key)));
    }
}
