package com.xiaobin.sql;

/**
 * 操纵数据库类
 */
public interface Dao {

    default int insert(){
        SqlFactory.SqlObj sqlObj = SqlFactory.insert(this);
        Dao2 dao2 = new Dao2();
        return dao2.exec(sqlObj.getSql(), sqlObj.getObjects());
    }

    default int update(){
        SqlFactory.SqlObj sqlObj = SqlFactory.update(this);
        Dao2 dao2 = new Dao2();
        return dao2.exec(sqlObj.getSql(), sqlObj.getObjects());
    }

    default int delete(){
        SqlFactory.SqlObj sqlObj = SqlFactory.delete(this);
        Dao2 dao2 = new Dao2();
        return dao2.exec(sqlObj.getSql(), sqlObj.getObjects());
    }
}
