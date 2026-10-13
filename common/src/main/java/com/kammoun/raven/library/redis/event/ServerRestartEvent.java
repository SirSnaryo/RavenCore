package com.kammoun.raven.library.redis.event;

public class ServerRestartEvent extends CrossServerEvent {

    private String targetServer;

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }
}
