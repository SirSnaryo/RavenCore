package com.kammoun.raven.library.locationpool;

public record RtpLocation(String serverId, LocationDimension dimension, String world,
                          double x, double y, double z, float yaw, float pitch) {}
