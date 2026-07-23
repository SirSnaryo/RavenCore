package com.kammoun.raven.library.locationpool;

public final class LocationPoolKeys {

    private LocationPoolKeys() {}

    public static String serverPool(String serverId, LocationDimension dimension) {
        return "locationpool:" + serverId + ":" + dimension.redisName();
    }

    public static String poolDefinition(String serverId, LocationDimension dimension) {
        return serverPool(serverId, dimension) + ":definition";
    }

    public static String clusterRegistry(String cluster, LocationDimension dimension) {
        return "raven:grid:" + cluster + ":" + dimension.registryName();
    }

    public static String globalGridRegistry(LocationDimension dimension) {
        return "raven:grid:global:" + dimension.registryName();
    }
}
