package com.xiaobin.conn;

/**
 * 数据库对象
 */
public abstract class ConnectionObj {

    private long lastUseTime;//上一次使用时间

    protected ConnectionObj() {
        setLastUseTime();
    }

    public void setLastUseTime() {
        this.lastUseTime = System.currentTimeMillis();
    }

    public long getLastUseTime() {
        return lastUseTime;
    }
}
