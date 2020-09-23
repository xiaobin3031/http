package com.xiaobin.conn.db;

import com.xiaobin.conn.ConnectionObj;

import java.sql.Connection;

public class DbConfig extends ConnectionObj {

    private Connection connection;

    DbConfig(Connection connection){
        super();
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }
}
