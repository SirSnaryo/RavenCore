package com.kammoun.raven.library.redis.event;

public abstract class CrossServerEvent {

    private String sourceServer;
    private long timestamp;

    public String getSourceServer() {
        return sourceServer;
    }

    public void setSourceServer(String sourceServer) {
        this.sourceServer = sourceServer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
