package com.kammoun.raven.portal.border;

import com.kammoun.raven.portal.model.ServerBorder;
import com.kammoun.raven.portal.model.ServerBorder.Axis;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Draws the particle wall a player sees as they approach a server border. */
public class BorderParticleRenderer {

    private final double detectionDistance;
    private final double particleSpacing;
    private final int wallHeight;
    private final Particle particle;

    public BorderParticleRenderer(double detectionDistance, double particleSpacing,
                                   int wallHeight, Particle particle) {
        this.detectionDistance = detectionDistance;
        this.particleSpacing   = particleSpacing;
        this.wallHeight        = wallHeight;
        this.particle          = particle;
    }

    /** Renders the wall for {@code border}, but only if the player is within detection range of it. */
    public void renderIfNear(Player player, ServerBorder border, Location playerLoc) {
        if (border.distanceTo(playerLoc.getX(), playerLoc.getZ()) > detectionDistance) return;

        double baseY = playerLoc.getY();

        if (border.getAxis() == Axis.X) {
            double fixedX = border.getBorderCoord();
            double zMin   = Math.max(border.getRangeMin(), playerLoc.getZ() - detectionDistance);
            double zMax   = Math.min(border.getRangeMax(), playerLoc.getZ() + detectionDistance);

            for (double z = zMin; z <= zMax; z += particleSpacing) {
                for (int dy = 0; dy < wallHeight; dy++) {
                    player.spawnParticle(particle, fixedX, baseY + dy, z, 1, 0, 0, 0, 0);
                }
            }
        } else {
            double fixedZ = border.getBorderCoord();
            double xMin   = Math.max(border.getRangeMin(), playerLoc.getX() - detectionDistance);
            double xMax   = Math.min(border.getRangeMax(), playerLoc.getX() + detectionDistance);

            for (double x = xMin; x <= xMax; x += particleSpacing) {
                for (int dy = 0; dy < wallHeight; dy++) {
                    player.spawnParticle(particle, x, baseY + dy, fixedZ, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}
