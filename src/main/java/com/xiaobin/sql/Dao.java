package com.xiaobin.sql;

/**
 * 操纵数据库类
 */
public interface Dao {

    default int insert(){
        return SqlFactory.insert(this);
    }

    default int update(){
        return SqlFactory.update(this);
    }

    default int delete(){
        return SqlFactory.delete(this);
    }
}
