package com.xiaobin.conn.db;

import com.xiaobin.conn.AbstractConnectionFactory;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * 数据库连接管理
 */
public class DbConnection extends AbstractConnectionFactory<DbObj> {

    private static final Logger logger = LoggerFactory.getLogger(DbConnection.class);

    private static final DbConnection instance = new DbConnection();

    /* 数据库连接参数 */
    private static String driverClass = "com.mysql.cj.jdbc.Driver";
    private static String url = "jdbc:mysql://127.0.0.1:3306/http?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
    private static String username = "http";
    private static String password = "http.1234";
    private static final String ACTIVE_SQL = "select 1";

    private DbConnection(){}

    static{
        String driverClass = System.getenv("db.driverClass");
        if(!Strkit.isEmpty(driverClass)){
            DbConnection.driverClass = driverClass;
        }
        String url = System.getenv("db.url");
        if(!Strkit.isEmpty(url)){
            DbConnection.url = String.format("jdbc:mysql://%s/http?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true", url);
        }
        String user = System.getenv("db.user");
        if(!Strkit.isEmpty(user)){
            DbConnection.username = user;
        }
        String password = System.getenv("db.password");
        if (!Strkit.isEmpty(password)) {
            DbConnection.password = password;
        }
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
    protected DbObj getSingleConn() throws Exception{
        Connection connection = DriverManager.getConnection(url, username, password);
        return new DbObj(connection);
    }

    @Override
    protected void refreshConn(DbObj dbObj) throws Exception{
        try(PreparedStatement preparedStatement = dbObj.getConnection().prepareStatement(ACTIVE_SQL)) {
            if(preparedStatement.execute()){
                dbObj.setLastUseTime();
            }else{
                throw new RuntimeException("执行sql失败：" + ACTIVE_SQL);
            }
        }
    }

    @Override
    public void close(DbObj dbObj) {
        if(dbObj.getConnection() != null){
            try {
                dbObj.getConnection().close();
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
