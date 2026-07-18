package com.kammoun.raven.time;

/** Snapshot of the overworld clock — day/night time plus weather — stored in Redis and mirrored by followers. */
public final class WorldClockState {

    public long fullTime;
    public boolean storm;
    public boolean thunder;
    public int weatherDuration;
    public int thunderDuration;

    public WorldClockState() {}

    public WorldClockState(long fullTime, boolean storm, boolean thunder,
                           int weatherDuration, int thunderDuration) {
        this.fullTime = fullTime;
        this.storm = storm;
        this.thunder = thunder;
        this.weatherDuration = weatherDuration;
        this.thunderDuration = thunderDuration;
    }
}
