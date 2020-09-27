package com.xiaobin.conn.db;

import com.xiaobin.conn.ConnectionObj;

import java.sql.Connection;

public class DbObj extends ConnectionObj {

    private final Connection connection;

    DbObj(Connection connection){
        super();
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }
}
