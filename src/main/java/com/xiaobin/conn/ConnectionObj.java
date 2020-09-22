package com.xiaobin.conn;

import java.sql.Connection;

/**
 * 数据库对象
 */
public class ConnectionObj {

    private final Connection connection;//连接
    private long lastUseTime;//上一次使用时间

    ConnectionObj(Connection connection) {
        this.connection = connection;
        setLastUseTime();
    }

    public Connection getConnection() {
        return connection;
    }

    void setLastUseTime() {
        this.lastUseTime = System.currentTimeMillis();
    }

    long getLastUseTime() {
        return lastUseTime;
    }
}
