package com.kammoun.raven.portal.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class ServerBorder {

    public enum Axis { X, Z }

    /** Buffer kept inside the owning server's territory when clamping a crossing back. */
    private static final double CLAMP_MARGIN = 0.25;

    /**
     * Buffer sent players land inside the neighbor's territory by, instead of right at the
     * line. Landing within a fraction of a block of the border let the neighbor's own
     * enforcement immediately see them as still (or again) overshooting and bounce them
     * straight back — a ping-pong loop between the two servers.
     */
    private static final double ARRIVAL_MARGIN = 3.0;

    @Getter
    private final Axis axis;
    @Getter
    private final double borderCoord; // the fixed X or Z value of the border line
    @Getter
    private final double rangeMin;    // the other axis range start
    @Getter
    private final double rangeMax;    // the other axis range end
    private final String serverNeg;   // server on the negative side
    private final String serverPos;   // server on the positive side

    public ServerBorder(Axis axis, double borderCoord,
                        double rangeMin, double rangeMax,
                        String serverNeg, String serverPos) {
        this.axis        = axis;
        this.borderCoord = borderCoord;
        this.rangeMin    = rangeMin;
        this.rangeMax    = rangeMax;
        this.serverNeg   = serverNeg;
        this.serverPos   = serverPos;
    }

    /** True if this border separates `server` from a neighbor. */
    public boolean involves(String server) {
        return serverNeg.equals(server) || serverPos.equals(server);
    }

    /** The server on the other side of this border from `server`, or null if unrelated. */
    public String otherServer(String server) {
        if (serverNeg.equals(server)) return serverPos;
        if (serverPos.equals(server)) return serverNeg;
        return null;
    }

    private boolean inPerpendicularRange(double x, double z) {
        double perpendicular = axis == Axis.X ? z : x;
        return perpendicular >= rangeMin && perpendicular <= rangeMax;
    }

    /** Distance from the player to this border line, or MAX_VALUE if out of range. */
    public double distanceTo(double x, double z) {
        if (!inPerpendicularRange(x, z)) return Double.MAX_VALUE;
        double coord = axis == Axis.X ? x : z;
        return Math.abs(coord - borderCoord);
    }

    /**
     * How far past this border `server`'s side has been breached, in blocks.
     * Zero or negative means the coordinate is still on `server`'s own side (or out of range).
     */
    public double overshoot(String server, double x, double z) {
        if (!inPerpendicularRange(x, z)) return -1;
        double coord = axis == Axis.X ? x : z;
        if (serverNeg.equals(server)) return coord - borderCoord;
        if (serverPos.equals(server)) return borderCoord - coord;
        return -1;
    }

    /** Clamps `coord` back onto `server`'s side of the border, leaving a small margin. */
    public double clampToOwnSide(String server, double coord) {
        if (serverNeg.equals(server)) return Math.min(coord, borderCoord - CLAMP_MARGIN);
        if (serverPos.equals(server)) return Math.max(coord, borderCoord + CLAMP_MARGIN);
        return coord;
    }

    /** The coordinate to send a player arriving from `fromServer`, safely inside the neighbor's side. */
    public double arrivalCoord(String fromServer) {
        if (serverNeg.equals(fromServer)) return borderCoord + ARRIVAL_MARGIN;
        if (serverPos.equals(fromServer)) return borderCoord - ARRIVAL_MARGIN;
        return borderCoord;
    }

    // -------------------------------------------------------------------------
    // Derivation — finds shared edges between adjacent regions

    /** Derives a border for every pair of regions that share an edge. */
    public static List<ServerBorder> deriveAll(List<OverworldRegion> regions) {
        List<ServerBorder> result = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            for (int j = i + 1; j < regions.size(); j++) {
                ServerBorder border = tryDerive(regions.get(i), regions.get(j));
                if (border != null) result.add(border);
            }
        }
        return result;
    }

    private static ServerBorder tryDerive(OverworldRegion a, OverworldRegion b) {
        // Shared X edge: a.maxX == b.minX (or vice versa), Z ranges overlap
        double sharedX = Double.NaN;
        String negServer = null, posServer = null;

        if (a.getMaxX() == b.getMinX()) {
            sharedX   = a.getMaxX();
            negServer = a.getServer();
            posServer = b.getServer();
        } else if (b.getMaxX() == a.getMinX()) {
            sharedX   = b.getMaxX();
            negServer = b.getServer();
            posServer = a.getServer();
        }

        if (!Double.isNaN(sharedX)) {
            double zMin = Math.max(a.getMinZ(), b.getMinZ());
            double zMax = Math.min(a.getMaxZ(), b.getMaxZ());
            if (zMax > zMin) {
                return new ServerBorder(Axis.X, sharedX, zMin, zMax, negServer, posServer);
            }
        }

        // Shared Z edge: a.maxZ == b.minZ (or vice versa), X ranges overlap
        double sharedZ = Double.NaN;

        if (a.getMaxZ() == b.getMinZ()) {
            sharedZ   = a.getMaxZ();
            negServer = a.getServer();
            posServer = b.getServer();
        } else if (b.getMaxZ() == a.getMinZ()) {
            sharedZ   = b.getMaxZ();
            negServer = b.getServer();
            posServer = a.getServer();
        } else {
            return null;
        }

        if (!Double.isNaN(sharedZ)) {
            double xMin = Math.max(a.getMinX(), b.getMinX());
            double xMax = Math.min(a.getMaxX(), b.getMaxX());
            if (xMax > xMin) {
                return new ServerBorder(Axis.Z, sharedZ, xMin, xMax, negServer, posServer);
            }
        }

        return null;
    }

}
