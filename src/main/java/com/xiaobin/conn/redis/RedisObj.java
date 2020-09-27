package com.xiaobin.conn.redis;

import com.xiaobin.conn.ConnectionObj;

import java.net.Socket;

public class RedisObj extends ConnectionObj {

    private final Socket socket;

    RedisObj(Socket socket) {
        this.socket = socket;
    }

    public Socket getSocket() {
        return socket;
    }
}
