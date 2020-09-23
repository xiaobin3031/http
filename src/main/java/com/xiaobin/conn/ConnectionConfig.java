package com.xiaobin.conn;

class ConnectionConfig {
    private static final int DEFAULT_MAX_KEEP = 10;
    private static final int DEFAULT_MIN_KEEP = 5;
    private static final int DEFAULT_INACTIVE_TIME = 3600_000;
    ConnectionConfig(){}

    private int maxKeep = 10;//最大连接数
    private int minKeep = 5;//最小连接数

    private int inactiveTime = 3600_000;//最小活动时间间隔，毫秒

    int getMaxKeep() {
        if(this.maxKeep <= 0){
            this.maxKeep = DEFAULT_MAX_KEEP;
        }
        return maxKeep;
    }

    void setMaxKeep(int maxKeep) {
        this.maxKeep = maxKeep;
    }

    int getMinKeep() {
        if(this.minKeep <= 0){
            this.minKeep = DEFAULT_MIN_KEEP;
        }
        return minKeep;
    }

    void setMinKeep(int minKeep) {
        this.minKeep = minKeep;
    }

    int getInactiveTime() {
        if(this.inactiveTime <= 1_000){
            this.inactiveTime = DEFAULT_INACTIVE_TIME;
        }
        return inactiveTime;
    }

    void setInactiveTime(int inactiveTime) {
        this.inactiveTime = inactiveTime;
    }
}
