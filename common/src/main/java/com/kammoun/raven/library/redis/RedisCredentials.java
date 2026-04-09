package com.kammoun.raven.library.redis;

import lombok.Getter;

@Getter
public class RedisCredentials {

    private final String host;
    private final int port;
    private final String password;
    private final int database;

    public RedisCredentials(String host, int port, String password, int database) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
}
