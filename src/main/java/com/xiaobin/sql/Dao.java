package com.xiaobin.sql;

import java.util.List;

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

    @SuppressWarnings("unchecked")
    default <T> T findById(Object object){
        return (T) SqlFactory.findById(this, object);
    }

    @SuppressWarnings("unchecked")
    default <T> List<T> find(){
        return (List<T>) SqlFactory.find(this);
    }
}
