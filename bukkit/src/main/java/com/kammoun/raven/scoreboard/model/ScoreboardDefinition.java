package com.kammoun.raven.scoreboard.model;

import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import org.bukkit.entity.Player;

import java.util.List;

public record ScoreboardDefinition(
        String name,
        int priority,
        String title,
        List<ScoreboardRequirement> requirements,
        List<ScoreboardLine> lines
) {

    public boolean matches(Player player, ScoreboardPlaceholders placeholders) {
        for (ScoreboardRequirement requirement : requirements) {
            if (!requirement.matches(player, placeholders)) {
                return false;
            }
        }
        return true;
    }
}
