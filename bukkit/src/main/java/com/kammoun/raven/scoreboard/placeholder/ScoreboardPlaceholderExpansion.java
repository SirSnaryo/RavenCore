package com.kammoun.raven.scoreboard.placeholder;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;

/**
 * Unified %raven_*% PlaceholderAPI expansion used by the scoreboard and other modules.
 */
public final class ScoreboardPlaceholderExpansion extends KPlaceholderExpansion {

    public ScoreboardPlaceholderExpansion(RavenCore plugin, ScoreboardPlaceholders placeholders) {
        super(plugin, "raven");
        placeholders.keys().forEach(key -> register(key, player -> placeholders.value(player, key)));
    }
}
