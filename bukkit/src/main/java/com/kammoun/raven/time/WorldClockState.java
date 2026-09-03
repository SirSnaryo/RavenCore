package com.kammoun.raven.time;

public final class WorldClockState {

    public long fullTime;
    public boolean storm;
    public boolean thunder;
    public int weatherDuration;
    public int thunderDuration;
    public boolean syncWeather;

    public WorldClockState() {}

    public WorldClockState(long fullTime, boolean storm, boolean thunder,
                           int weatherDuration, int thunderDuration) {
        this(fullTime, storm, thunder, weatherDuration, thunderDuration, false);
    }

    public WorldClockState(long fullTime, boolean storm, boolean thunder,
                           int weatherDuration, int thunderDuration, boolean syncWeather) {
        this.fullTime = fullTime;
        this.storm = storm;
        this.thunder = thunder;
        this.weatherDuration = weatherDuration;
        this.thunderDuration = thunderDuration;
        this.syncWeather = syncWeather;
    }
}
