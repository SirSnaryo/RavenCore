package com.kammoun.raven.scoreboard.model;

import com.kammoun.raven.scoreboard.placeholder.ScoreboardPlaceholders;
import org.bukkit.entity.Player;

public record ScoreboardRequirement(String type, String value, String placeholder, String expected) {

    public boolean matches(Player player, ScoreboardPlaceholders placeholders) {
        return switch (type.toLowerCase()) {
            case "permission" -> value != null && player.hasPermission(value);
            case "world" -> value != null && player.getWorld().getName().equalsIgnoreCase(value);
            case "server-role" -> value != null
                    && placeholders.getPlugin().getServerRole().name().equalsIgnoreCase(value);
            case "server" -> value != null
                    && placeholders.getPlugin().getServerId().equalsIgnoreCase(value);
            case "placeholder-equals" -> expected != null
                    && placeholders.apply(player, placeholder).equalsIgnoreCase(expected);
            case "placeholder-contains" -> expected != null
                    && placeholders.apply(player, placeholder).toLowerCase().contains(expected.toLowerCase());
            default -> false;
        };
    }
}
