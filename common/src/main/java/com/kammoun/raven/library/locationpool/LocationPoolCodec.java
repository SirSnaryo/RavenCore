package com.kammoun.raven.library.locationpool;

import java.util.Optional;

public final class LocationPoolCodec {

    private LocationPoolCodec() {}

    public static String encode(RtpLocation location) {
        return location.world() + "," + location.x() + "," + location.y() + "," + location.z() + ","
                + location.yaw() + "," + location.pitch();
    }

    public static Optional<RtpLocation> decode(String serverId, LocationDimension dimension, String payload) {
        if (payload == null) {
            return Optional.empty();
        }
        String[] values = payload.split(",", -1);
        if (values.length != 6) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RtpLocation(serverId, dimension, values[0],
                    Double.parseDouble(values[1]), Double.parseDouble(values[2]),
                    Double.parseDouble(values[3]), Float.parseFloat(values[4]),
                    Float.parseFloat(values[5])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
