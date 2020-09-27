package com.xiaobin.sql;

import com.xiaobin.conn.db.DbObj;
import com.xiaobin.conn.db.DbConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * 事务
 */
public class Transaction {

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);

    public static <T> T exec(Supplier<T> supplier){
        T t = null;
        if(supplier != null){
            long id = Thread.currentThread().getId();
            if(DbConnection.getInstance().getConn(id) == null){
                DbConnection.getInstance().inThread(id, DbConnection.getInstance().getConn());
            }
            DbObj dbObj = DbConnection.getInstance().getConn(id);
            try{
                t = supplier.get();
                dbObj.getConnection().commit();
            }catch(Exception e){
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
                if(dbObj != null){
                    try {
                        dbObj.getConnection().rollback();
                    } catch (SQLException e1) {
                        if(logger.isErrorEnabled()){
                            logger.error("回滚失败: " + e.getMessage(), e1);
                        }
                    }
                }
            } finally{
                DbConnection.getInstance().removeFromThread(id);
            }
        }
        return t;
    }
}
