package com.xiaobin.conn.redis;

class RedisConfig {

    private String host;
    private int port;

    String getHost() {
        return host;
    }

    void setHost(String host) {
        this.host = host;
    }

    int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }
}
