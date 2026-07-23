package com.kammoun.raven.library.locationpool;

public enum LocationDimension {
    OVERWORLD("overworld", "NORMAL"),
    NETHER("nether", "NETHER"),
    END("end", "THE_END");

    private final String redisName;
    private final String registryName;

    LocationDimension(String redisName, String registryName) {
        this.redisName = redisName;
        this.registryName = registryName;
    }

    public String redisName() {
        return redisName;
    }

    public String registryName() {
        return registryName;
    }
}
