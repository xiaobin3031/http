package com.xiaobin.conn.db;

import com.xiaobin.conn.AbstractConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * 数据库连接管理
 */
public class DbConnection extends AbstractConnectionFactory<DbConfig> {

    private static final Logger logger = LoggerFactory.getLogger(DbConnection.class);

    private static final DbConnection instance = new DbConnection();

    /* 数据库连接参数 */
    private static final String driverClass = "com.mysql.cj.jdbc.Driver";
    private static final String url = "jdbc:mysql://127.0.0.1:3306/http?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String username = "http";
    private static final String password = "http.1234";
    private static final String ACTIVE_SQL = "select 1";

    private DbConnection(){}

    static{
        instance._init();
    }

    public static DbConnection getInstance() {
        return instance;
    }

    @Override
    protected void init() {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            if(logger.isErrorEnabled()){
                logger.error("数据库连接初始化失败: " + e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    protected DbConfig getSingleConn() throws Exception{
        Connection connection = DriverManager.getConnection(url, username, password);
        return new DbConfig(connection);
    }

    @Override
    protected void refreshConn(DbConfig dbConfig) throws Exception{
        try(PreparedStatement preparedStatement = dbConfig.getConnection().prepareStatement(ACTIVE_SQL)) {
            if(preparedStatement.execute()){
                dbConfig.setLastUseTime();
            }else{
                throw new RuntimeException("执行sql失败：" + ACTIVE_SQL);
            }
        }
    }

    @Override
    protected void close(DbConfig dbConfig) {
        if(dbConfig != null && dbConfig.getConnection() != null){
            try {
                dbConfig.getConnection().close();
            } catch (SQLException e) {
                if(logger.isErrorEnabled()){
                    logger.error("关闭Connection失败: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 关闭ps
     * @param preparedStatement ps
     */
    public void close(PreparedStatement preparedStatement){
        if(preparedStatement != null){
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                if(logger.isErrorEnabled()){
                    logger.error("关闭preparedStatement失败: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 关闭ps和rs
     * @param preparedStatement ps
     * @param resultSet rs
     */
    public void close(PreparedStatement preparedStatement, ResultSet resultSet){
        close(preparedStatement);
        if(resultSet != null){
            try {
                resultSet.close();
            } catch (SQLException e) {
                if(logger.isErrorEnabled()){
                    logger.error("关闭resultSet失败: " + e.getMessage(), e);
                }
            }
        }
    }
}
