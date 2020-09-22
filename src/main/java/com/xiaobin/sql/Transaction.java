package com.xiaobin.sql;

import com.xiaobin.conn.ConnectionFactory;
import com.xiaobin.conn.ConnectionObj;
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
            if(ConnectionFactory.getConn(id) == null){
                ConnectionFactory.add(id, ConnectionFactory.getConn());
            }
            ConnectionObj connectionObj = ConnectionFactory.getConn(id);
            try{
                t = supplier.get();
                connectionObj.getConnection().commit();
            }catch(Exception e){
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
                if(connectionObj != null){
                    try {
                        connectionObj.getConnection().rollback();
                    } catch (SQLException e1) {
                        if(logger.isErrorEnabled()){
                            logger.error("回滚失败: " + e.getMessage(), e1);
                        }
                    }
                }
            } finally{
                ConnectionFactory.close(id);
            }
        }
        return t;
    }
}
