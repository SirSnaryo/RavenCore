package com.kammoun.raven.rtp.queue;

import com.kammoun.raven.library.locationpool.RtpLocation;

import java.util.concurrent.ThreadLocalRandom;

record RtpPairTarget(RtpLocation first, RtpLocation second) {

    static RtpPairTarget from(RtpLocation base, double distance) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;

        double firstX = base.x();
        double firstZ = base.z();
        double secondX = firstX + dx;
        double secondZ = firstZ + dz;

        RtpLocation first = new RtpLocation(base.serverId(), base.dimension(), base.world(),
                firstX, base.y(), firstZ, yawTo(firstX, firstZ, secondX, secondZ), 0.0f);
        RtpLocation second = new RtpLocation(base.serverId(), base.dimension(), base.world(),
                secondX, base.y(), secondZ, yawTo(secondX, secondZ, firstX, firstZ), 0.0f);
        return new RtpPairTarget(first, second);
    }

    private static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
