package com.xiaobin.sql;

import com.xiaobin.conn.db.DbConfig;
import com.xiaobin.conn.db.DbConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 数据库执行类
 */
public class Dao2 {

    private static final AtomicLong atomicLong = new AtomicLong(0);
    private static final Logger logger = LoggerFactory.getLogger(Dao2.class);

    private static final int BATCH_MAX_COUNT = 1000;
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
                    if(logger.isErrorEnabled()){
                        logger.error("设置preparedStatement出错: " + e.getMessage(), e);
                    }
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
                if(logger.isErrorEnabled()){
                    logger.error("回滚失败: " + e.getMessage(), e);
                }
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
        DbConfig dbConfig = DbConnection.getInstance().getConn(Thread.currentThread().getId());
        int result;
        if(dbConfig == null){
            result = exec(sql, DbConnection.getInstance().getConn(), objects);
        }else{
            result = execTransaction(sql, dbConfig, objects);
        }
        return result;
    }

    /**
     * 执行无事务的sql
     * @param sql sql
     * @param dbConfig 连接
     * @param objects 参数
     * @return 影响的结果
     */
    private int exec(String sql, DbConfig dbConfig, Object... objects){
        int result = 0;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            boolean commit = dbConfig.getConnection().getAutoCommit();
            dbConfig.getConnection().setAutoCommit(false);
            preparedStatement = dbConfig.getConnection().prepareStatement(sql);
            setArguments(preparedStatement, objects);
            result = preparedStatement.executeUpdate();
            dbConfig.getConnection().commit();
            dbConfig.getConnection().setAutoCommit(commit);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
            rollback(dbConfig.getConnection());
        }finally{
            DbConnection.getInstance().inPool(dbConfig);
            DbConnection.getInstance().close(preparedStatement);
        }
        printResult(id, result);
        return result;
    }

    /**
     * 执行事务的sql
     * @param sql sql
     * @param dbConfig 连接
     * @param objects 参数
     * @return 影响的参数
     */
    private int execTransaction(String sql, DbConfig dbConfig, Object... objects){
        int result;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            dbConfig.getConnection().setAutoCommit(false);
            preparedStatement = dbConfig.getConnection().prepareStatement(sql);
            setArguments(preparedStatement, objects);
            result = preparedStatement.executeUpdate();
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
            //XWB-2020/8/25- 需要抛出异常来让事务类捕获，使之回滚
            throw new RuntimeException(e);
        }finally{
            DbConnection.getInstance().close(preparedStatement);
        }
        printResult(id, result);
        return result;
    }

    /**
     * 执行批量sql 执行结果条数可能不准确，尽量不要使用
     * @param sql sql
     * @param list 参数
     * @return 成功结果数
     */
    public int execList(String sql, List<Object[]> list){
        int result = 0;
        DbConfig dbConfig = null;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        try{
            dbConfig = DbConnection.getInstance().getConn();
            boolean commit = dbConfig.getConnection().getAutoCommit();
            dbConfig.getConnection().setAutoCommit(false);
            preparedStatement = dbConfig.getConnection().prepareStatement(sql);
            int count = 0;
            if(list != null && !list.isEmpty()){
                for(Object[] objects: list){
                    printArguments(id, objects);
                    setArguments(preparedStatement, objects);
                    preparedStatement.addBatch();
                    count++;
                    if(count % BATCH_MAX_COUNT == 0){
                        int[] results = preparedStatement.executeBatch();
                        result += Arrays.stream(results).filter(i -> i > 0).sum();
                        count = 0;
                    }
                }
                if(count > 0){
                    int[] results = preparedStatement.executeBatch();
                    result += Arrays.stream(results).filter(i -> i > 0).sum();
                }
            }
            dbConfig.getConnection().commit();
            dbConfig.getConnection().setAutoCommit(commit);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行insert失败: ", id, e);
            }
            if(dbConfig != null){
                rollback(dbConfig.getConnection());
            }
        }finally{
            DbConnection.getInstance().inPool(dbConfig);
            DbConnection.getInstance().close(preparedStatement);
        }
        printResult(id, result + "/" + (list == null ? 0 : list.size()));
        return result;
    }

    public <T> T find(Function<ResultSet, T> function, String sql, Object... objects){
        DbConfig dbConfig = DbConnection.getInstance().getConn(Thread.currentThread().getId());
        if(dbConfig == null){
            return find(function, sql, DbConnection.getInstance().getConn(), true, objects);
        }else{
            return find(function, sql, dbConfig, false, objects);
        }
    }

    private <T> T find(Function<ResultSet, T> function, String sql, DbConfig dbConfig, boolean needAdd, Object... objects) {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            preparedStatement = dbConfig.getConnection().prepareStatement(sql);
            setArguments(preparedStatement, objects);
            resultSet = preparedStatement.executeQuery();
            if(function == null){
                return null;
            }
            return function.apply(resultSet);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
        }finally{
            printResult(id, fetchSize(resultSet));
            if(needAdd){
                DbConnection.getInstance().inPool(dbConfig);
            }
            DbConnection.getInstance().close(preparedStatement, resultSet);
        }
        return null;
    }

    /**
     * 计算查询总条数
     * @param resultSet resultSet
     * @return 总条数
     */
    private long fetchSize(ResultSet resultSet){
        long index = 0;
        if(resultSet != null){
            try{
                while(resultSet.next()){
                    index++;
                }
            }catch(SQLException e){
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            } finally{
                try {
                    resultSet.beforeFirst();
                } catch (SQLException sqlException) {
                    if(logger.isErrorEnabled()){
                        logger.error(sqlException.getMessage(), sqlException);
                    }
                }
            }
        }
        return index;
    }
}
