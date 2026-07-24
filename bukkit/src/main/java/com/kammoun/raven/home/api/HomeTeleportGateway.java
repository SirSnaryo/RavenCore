package com.kammoun.raven.home.api;

import org.bukkit.entity.Player;

/**
 * Public Home-module integration point for server-aware home destinations.
 */
@FunctionalInterface
public interface HomeTeleportGateway {
    void teleport(Player player, HomeDestination destination);
}
