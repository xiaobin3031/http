package com.xiaobin.sql;

import com.xiaobin.conn.db.DbConnection;
import com.xiaobin.conn.db.DbObj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 数据库执行类
 */
public class Dao {

    private static final AtomicLong atomicLong = new AtomicLong(0);
    private static final Logger logger = LoggerFactory.getLogger(Dao.class);

    /**
     * 打印参数
     * @param objects 参数列表
     */
    private void printArguments(long id, Object... objects){
        if(logger.isDebugEnabled()){
            logger.debug("ID: {}, {}", id, objects);
        }
    }

    /**
     * 打印sql
     * @param sql sql
     */
    private void printSql(long id, String sql){
        if(logger.isDebugEnabled()){
            logger.debug("ID: {}, {}", id, sql);
        }
    }

    /**
     * 打印结果
     * @param id id
     * @param result 结果
     */
    private void printResult(long id, Object result){
        if(logger.isDebugEnabled()){
            logger.debug("ID: {}, update: {}", id, result);
        }
    }

    private void setArguments(PreparedStatement preparedStatement, Object... objects){
        if(objects != null && objects.length > 0){
            for(int i=0; i< objects.length; i++){
                try {
                    preparedStatement.setObject(i + 1, objects[i]);
                } catch (SQLException e) {
                    logSqlException(e);
                    throw new RuntimeException(e);
                }
            }
        }
    }
    /**
     * 回退
     * @param connection 连接
     */
    private void rollback(Connection connection){
        if(connection != null){
            try {
                connection.rollback();
            } catch (SQLException e) {
                logSqlException(e);
            }
        }
    }

    /**
     * 执行sql
     * @param sql sql
     * @param objects 参数
     * @return 影响的条数
     */
    public int exec(String sql, Object... objects){
        DbObj dbObj = DbConnection.getInstance().getConn(Thread.currentThread().getId());
        int result;
        if(dbObj == null){
            result = exec(sql, DbConnection.getInstance().getConn(), objects);
        }else{
            result = execTransaction(sql, dbObj, objects);
        }
        return result;
    }

    /**
     * 执行无事务的sql
     * @param sql sql
     * @param dbObj 连接
     * @param objects 参数
     * @return 影响的结果
     */
    private int exec(String sql, DbObj dbObj, Object... objects){
        int result = 0;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            boolean commit = dbObj.getConnection().getAutoCommit();
            dbObj.getConnection().setAutoCommit(false);
            preparedStatement = dbObj.getConnection().prepareStatement(sql);
            setArguments(preparedStatement, objects);
            result = preparedStatement.executeUpdate();
            dbObj.getConnection().commit();
            dbObj.getConnection().setAutoCommit(commit);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id);
            }
            logSqlException(e);
            rollback(dbObj.getConnection());
        }finally{
            DbConnection.getInstance().inPool(dbObj);
            DbConnection.getInstance().close(preparedStatement);
        }
        printResult(id, result);
        return result;
    }

    /**
     * 执行事务的sql
     * @param sql sql
     * @param dbObj 连接
     * @param objects 参数
     * @return 影响的参数
     */
    private int execTransaction(String sql, DbObj dbObj, Object... objects){
        int result;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            dbObj.getConnection().setAutoCommit(false);
            preparedStatement = dbObj.getConnection().prepareStatement(sql);
            setArguments(preparedStatement, objects);
            result = preparedStatement.executeUpdate();
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id);
            }
            logSqlException(e);
            //XWB-2020/8/25- 需要抛出异常来让事务类捕获，使之回滚
            throw new RuntimeException(e);
        }finally{
            DbConnection.getInstance().close(preparedStatement);
        }
        printResult(id, result);
        return result;
    }

    public <T> T find(Function<ResultSet, T> function, String sql, Object... objects){
        DbObj dbObj = DbConnection.getInstance().getConn(Thread.currentThread().getId());
        if(dbObj == null){
            return find(function, sql, DbConnection.getInstance().getConn(), true, objects);
        }else{
            return find(function, sql, dbObj, false, objects);
        }
    }

    private <T> T find(Function<ResultSet, T> function, String sql, DbObj dbObj, boolean needAdd, Object... objects) {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            preparedStatement = dbObj.getConnection().prepareStatement(sql);
            setArguments(preparedStatement, objects);
            resultSet = preparedStatement.executeQuery();
            if(function == null){
                return null;
            }
            return function.apply(resultSet);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id);
            }
            logSqlException(e);
        }finally{
            printResult(id, fetchSize(resultSet));
            if(needAdd){
                DbConnection.getInstance().inPool(dbObj);
            }
            DbConnection.getInstance().close(preparedStatement, resultSet);
        }
        return null;
    }

    private void logSqlException(Exception e){
        if(logger.isErrorEnabled()){
            if(e instanceof SQLException){
                String msg = e.getMessage();
                if (msg.contains("Duplicate entry")) {
                    logger.error(msg);
                }else{
                    logger.error(msg, e);
                }
            }else{
                logger.error(e.getMessage(), e);
            }
        }
    }
    /**
     * 计算查询总条数
     * @param resultSet resultSet
     * @return 总条数
     */
    private long fetchSize(ResultSet resultSet){
        if(resultSet != null){
            try{
                resultSet.last();
                return resultSet.getRow();
            }catch(Exception e){
                logSqlException(e);
            } finally{
                try {
                    resultSet.beforeFirst();
                } catch (Exception sqlException) {
                    logSqlException(sqlException);
                }
            }
        }
        return 0;
    }
}
