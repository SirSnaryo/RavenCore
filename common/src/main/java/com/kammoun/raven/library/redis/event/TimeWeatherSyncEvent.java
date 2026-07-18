package com.kammoun.raven.library.redis.event;

/**
 * Broadcast by the elected time authority every interval so every other survival server can
 * mirror the overworld clock — day/night time plus rain/thunder. Serialized as-is by Gson, so
 * the fields are public and there is a no-arg constructor for deserialization.
 */
public class TimeWeatherSyncEvent extends CrossServerEvent {

    public long fullTime;
    public boolean storm;
    public boolean thunder;
    public int weatherDuration;
    public int thunderDuration;

    public TimeWeatherSyncEvent() {}

    public TimeWeatherSyncEvent(long fullTime, boolean storm, boolean thunder,
                                int weatherDuration, int thunderDuration) {
        this.fullTime = fullTime;
        this.storm = storm;
        this.thunder = thunder;
        this.weatherDuration = weatherDuration;
        this.thunderDuration = thunderDuration;
    }
}
