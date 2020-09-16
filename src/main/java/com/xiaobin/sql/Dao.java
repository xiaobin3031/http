package com.xiaobin.sql;

import com.xiaobin.conn.ConnectionFactory;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 数据库执行类
 */
public class Dao {

    private static final AtomicLong atomicLong = new AtomicLong(0);
    private static final Logger logger = LoggerFactory.getLogger(Dao.class);

    private static final ConcurrentMap<String, Map<String, Transfer>> transferMap = new ConcurrentHashMap<>();
    private static final Map<String, BiFunction<ResultSet, Integer, Object>> columnTransferMap = new HashMap<>();

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
        Connection connection = ConnectionFactory.getConn(Thread.currentThread().getId());
        int result;
        if(connection == null){
            result = exec(sql, ConnectionFactory.getConn(), objects);
        }else{
            result = execTransaction(sql, connection, objects);
        }
        return result;
    }

    /**
     * 执行无事务的sql
     * @param sql sql
     * @param connection 连接
     * @param objects 参数
     * @return 影响的结果
     */
    private int exec(String sql, Connection connection, Object... objects){
        int result = 0;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            boolean commit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            preparedStatement = connection.prepareStatement(sql);
            setArguments(preparedStatement, objects);
            result = preparedStatement.executeUpdate();
            connection.commit();
            connection.setAutoCommit(commit);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
            rollback(connection);
        }finally{
            ConnectionFactory.add(connection);
            ConnectionFactory.close(preparedStatement);
        }
        printResult(id, result);
        return result;
    }

    /**
     * 执行事务的sql
     * @param sql sql
     * @param connection 连接
     * @param objects 参数
     * @return 影响的参数
     */
    private int execTransaction(String sql, Connection connection, Object... objects){
        int result;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            connection.setAutoCommit(false);
            preparedStatement = connection.prepareStatement(sql);
            setArguments(preparedStatement, objects);
            result = preparedStatement.executeUpdate();
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
            //XWB-2020/8/25- 需要抛出异常来让事务类捕获，使之回滚
            throw new RuntimeException(e);
        }finally{
            ConnectionFactory.close(preparedStatement);
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
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        try{
            connection = ConnectionFactory.getConn();
            boolean commit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            preparedStatement = connection.prepareStatement(sql);
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
            connection.commit();
            connection.setAutoCommit(commit);
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行insert失败: ", id, e);
            }
            rollback(connection);
        }finally{
            ConnectionFactory.add(connection);
            ConnectionFactory.close(preparedStatement);
        }
        printResult(id, result + "/" + (list == null ? 0 : list.size()));
        return result;
    }

    /**
     * 结果集是否存在
     * @param sql sql
     * @param objects 数据级
     * @return true: exist, false: not exist
     */
    public boolean exist(String sql, Object... objects){
        Connection connection = ConnectionFactory.getConn(Thread.currentThread().getId());
        if(connection == null){
            return exist(sql, ConnectionFactory.getConn(), true, objects);
        }else{
            return exist(sql, connection, false, objects);
        }
    }

    /**
     * 结果集是否存在
     * @param sql sql
     * @param connection 连接
     * @param needAdd 是否需要加入连接池，true: 加入；false: 不加入
     * @param objects 参数
     * @return true: exist, false: not exist
     */
    private boolean exist(String sql, Connection connection, boolean needAdd, Object... objects){
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        boolean exist = false;
        try{
            connection = ConnectionFactory.getConn();
            preparedStatement = connection.prepareStatement(sql);
            setArguments(preparedStatement, objects);
            resultSet = preparedStatement.executeQuery();
            exist = resultSet.next();
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
        }finally{
            if(needAdd){
                ConnectionFactory.add(connection);
            }
            ConnectionFactory.close(preparedStatement, resultSet);
        }
        printResult(id, exist);
        return exist;
    }

    /**
     * 查询map， 先返回map，后续再格式化成类
     * @param sql sql
     * @param objects 参数
     * @return 结果集
     */
    public Map<String, Object> findOne(String sql, Object... objects){
        Connection connection = ConnectionFactory.getConn(Thread.currentThread().getId());
        if(connection == null){
            return findOne(sql, ConnectionFactory.getConn(), true, objects);
        }else{
            return findOne(sql, connection, false, objects);
        }
    }

    /**
     * 支持在事务中查询
     * @param sql sql
     * @param connection 连接
     * @param needAdd 是否需要加入连接池，true: 加入；false: 不加入
     * @param objects 参数
     * @return 结果集
     */
    private Map<String, Object> findOne(String sql, Connection connection, boolean needAdd, Object... objects){
        Map<String, Object> result = new HashMap<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            connection = ConnectionFactory.getConn();
            preparedStatement = connection.prepareStatement(sql);
            setArguments(preparedStatement, objects);
            resultSet = preparedStatement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int count = metaData.getColumnCount(), index;
            if(resultSet.next()){
                for(index = 1; index <= count; index++){
                    result.put(metaData.getColumnName(index), getValue(resultSet, index, metaData.getColumnType(index)));
                }
            }
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
        }finally{
            if(needAdd){
                ConnectionFactory.add(connection);
            }
            ConnectionFactory.close(preparedStatement, resultSet);
        }
        printResult(id, 1);
        return result;
    }
    /**
     * 查询list， 先返回map，后续再格式化成类
     * @param sql sql
     * @param objects 参数
     * @return 结果集
     */
    public List<Map<String, Object>> findList(String sql, Object... objects){
        Connection connection = ConnectionFactory.getConn(Thread.currentThread().getId());
        if(connection == null){
            return findList(sql, ConnectionFactory.getConn(), true, objects);
        }else{
            return findList(sql, connection, false, objects);
        }
    }

    /**
     * 支持在事务中查询
     * @param sql sql
     * @param connection 连接
     * @param needAdd 是否需要重新加入连接池，true: 加入；false: 不加入
     * @param objects 参数
     * @return 结果集
     */
    private List<Map<String, Object>> findList(String sql, Connection connection, boolean needAdd, Object... objects){
        List<Map<String, Object>> resultList = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        try{
            preparedStatement = connection.prepareStatement(sql);
            setArguments(preparedStatement, objects);
            resultSet = preparedStatement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int count = metaData.getColumnCount(), index;
            while(resultSet.next()){
                Map<String, Object> result = new HashMap<>();
                for(index = 1; index <= count; index++){
                    result.put(metaData.getColumnName(index), getValue(resultSet, index, metaData.getColumnType(index)));
                }
                resultList.add(result);
            }
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
        }finally{
            if(needAdd){
                ConnectionFactory.add(connection);
            }
            ConnectionFactory.close(preparedStatement, resultSet);
        }
        printResult(id, resultList.size());
        return resultList;
    }

    private Object getValue(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.INTEGER:
                return resultSet.getInt(index);
            case Types.DATE:
                return resultSet.getDate(index);
            case Types.FLOAT:
                return resultSet.getFloat(index);
            case Types.DOUBLE:
                return resultSet.getDouble(index);
            case Types.BIGINT:
                return new BigInteger(String.valueOf(resultSet.getLong(index)));
            case Types.BOOLEAN:
                return resultSet.getBoolean(index);
            case Types.TIMESTAMP:
                return resultSet.getTimestamp(index);
            case Types.BLOB:
                return resultSet.getBlob(index);
            case Types.CLOB:
                return resultSet.getClob(index);
            case Types.NCLOB:
                return resultSet.getNClob(index);
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.CHAR:
                return resultSet.getString(index);
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.NCHAR:
                return resultSet.getNString(index);
            default:
                return resultSet.getObject(index);
        }
    }
    public <T> T findOne(String sql, Class<T> clazz, Object[] objects){
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long id = atomicLong.incrementAndGet();
        printSql(id, sql);
        printArguments(id, objects);
        T t = null;
        try{
            connection = ConnectionFactory.getConn();
            preparedStatement = connection.prepareStatement(sql);
            setArguments(preparedStatement, objects);
            resultSet = preparedStatement.executeQuery();
            while(resultSet.next()){

            }
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("ID: {}, 执行sql失败: ", id, e);
            }
        }finally{
            ConnectionFactory.add(connection);
            ConnectionFactory.close(preparedStatement, resultSet);
        }
        return t;
    }

    private <T> T reflect(ResultSet resultSet, Class<T> clazz){
        T t = null;
        try {
            t = clazz.newInstance();
            Map<String, Transfer> map = transferMap.get(clazz.getName());
            ResultSetMetaData metaData = resultSet.getMetaData();
            if(map == null || map.isEmpty()){
                Field[] fields = clazz.getDeclaredFields();
                if(fields.length > 0){
                    for(Field field: fields){
                        String name = field.getName();
                        Method method = clazz.getMethod("set" + Strkit.upperAtFirst(name));
                        if(method.isAccessible()){
                            Class<?>[] paramTypes = method.getParameterTypes();
                            if(paramTypes.length > 0){

                            }
                        }
                    }
                }
            }else{

            }
        } catch (InstantiationException | IllegalAccessException e) {
            if(logger.isErrorEnabled()){
                logger.error("new实体类失败: {}", e.getMessage(), e);
            }
        } catch(NoSuchMethodException | SQLException e){
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
        }
        return t;
    }

    /**
     * 将数据库的类型转换成实体类的类型
     * @param clazz 实体类类型
     * @param resultSet 数据库
     * @param index 数据库字段索引
     * @param type 数据库类型值
     * @param typeName 数据库类型名称
     * @return 实体类变量值
     */
    private Object transfer(Class<?> clazz, ResultSet resultSet, int index, int type, String typeName){
        if(clazz != null){
            try{
                if (Date.class.equals(clazz)) {
                    return getSqlDate(resultSet, index, type);
                } else if (java.util.Date.class.equals(clazz)) {
                    return getUtilDate(resultSet, index, type);
                } else if (Integer.class.equals(clazz)) {
                    return getInt(resultSet, index, type);
                } else if (Long.class.equals(clazz)){
                    return getInt(resultSet, index, type).longValue();
                } else if (Float.class.equals(clazz)){
                    return getFloat(resultSet, index, type);
                } else if (Double.class.equals(clazz)){
                    return getDouble(resultSet, index, type);
                } else if (Short.class.equals(clazz)){
                    return getShort(resultSet, index, type);
                } else if (Boolean.class.equals(clazz)) {
                    return getBoolean(resultSet, index, type);
                }
            }catch(Exception e){
                if(logger.isErrorEnabled()){
                    logger.error("尝试将{}转换成{}失败: {}", typeName, clazz.getName(), e.getMessage(), e);
                }
            }
        }
        return null;
    }

    private Boolean getBoolean(ResultSet resultSet, int index, int type) throws SQLException {
        if (type == Types.BOOLEAN) {
            return resultSet.getBoolean(index);
        }
        throw new RuntimeException("unknown");
    }
    private Short getShort(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.SMALLINT:
            case Types.TINYINT:
                return resultSet.getShort(index);
            default:
                throw new RuntimeException("unknown");
        }
    }
    private Double getDouble(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.FLOAT:
            case Types.DOUBLE:
                return resultSet.getDouble(index);
            default:
                throw new RuntimeException("unknown");
        }
    }
    private Float getFloat(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.FLOAT:
                return resultSet.getFloat(index);
            default:
                throw new RuntimeException("unknown");
        }
    }
    private Integer getInt(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
                return resultSet.getInt(index);
            default:
                throw new RuntimeException("unknown");
        }
    }
    private java.sql.Date getSqlDate(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.DATE:
                return resultSet.getDate(index);
            case Types.TIMESTAMP:
                Timestamp timestamp = resultSet.getTimestamp(index);
                return new Date(timestamp.getTime());
            default:
                throw new RuntimeException("unknown");
        }
    }

    private java.util.Date getUtilDate(ResultSet resultSet, int index, int type) throws SQLException {
        switch(type){
            case Types.DATE:
                Date date = resultSet.getDate(index);
                return new java.util.Date(date.getTime());
            case Types.TIMESTAMP:
                Timestamp timestamp = resultSet.getTimestamp(index);
                return new java.util.Date(timestamp.getTime());
            default:
                throw new RuntimeException("unknown");
        }
    }

    /**
     * 类型转换
     */
    private static class Transfer{

        private Method pojoMethod;
        private Function<ResultSet, Object> function;

        void setPojoMethod(Method pojoMethod) {
            this.pojoMethod = pojoMethod;
        }

        void setFunction(Function<ResultSet, Object> function) {
            this.function = function;
        }

        <T> void accept(T t, ResultSet resultSet){
            try {
                pojoMethod.invoke(t, function.apply(resultSet));
            } catch (IllegalAccessException | InvocationTargetException e) {
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }
}
