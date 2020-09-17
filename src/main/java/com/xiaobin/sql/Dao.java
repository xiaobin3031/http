package com.xiaobin.sql;

import java.lang.reflect.Field;

/**
 * 操纵数据库类
 */
public interface Dao {

    default int add(){
        Field[] fields = this.getClass().getDeclaredFields();
        for(Field field: fields){
            System.out.println(field);
        }
        return 0;
    }
}
