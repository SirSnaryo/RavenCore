package com.kammoun.raven.library.redis.event;

public class TimeWeatherSyncEvent extends CrossServerEvent {

    public long fullTime;
    public boolean storm;
    public boolean thunder;
    public int weatherDuration;
    public int thunderDuration;
    public boolean syncWeather;
    public String scope;

    public TimeWeatherSyncEvent() {}

    public TimeWeatherSyncEvent(long fullTime, boolean storm, boolean thunder,
                                int weatherDuration, int thunderDuration) {
        this(fullTime, storm, thunder, weatherDuration, thunderDuration, false, "survival");
    }

    public TimeWeatherSyncEvent(long fullTime, boolean storm, boolean thunder,
                                int weatherDuration, int thunderDuration,
                                boolean syncWeather, String scope) {
        this.fullTime = fullTime;
        this.storm = storm;
        this.thunder = thunder;
        this.weatherDuration = weatherDuration;
        this.thunderDuration = thunderDuration;
        this.syncWeather = syncWeather;
        this.scope = scope;
    }
}
