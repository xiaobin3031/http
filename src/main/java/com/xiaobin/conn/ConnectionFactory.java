package com.xiaobin.conn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据库连接管理
 */
public class ConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionFactory.class);
    private static final ConcurrentLinkedQueue<Connection> connectionConcurrentLinkedQueue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentMap<Long, Connection> connectionConcurrentMap = new ConcurrentHashMap<>();

    private static final ConnectionFactory instance = new ConnectionFactory();

    private static volatile boolean replenish = false;//是否正在添加连接
    private static final int maxKeep = 80; //最大保持的连接数
    private static final int minKeep = 50; //最小保持的连接数
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    /* 数据库连接参数 */
    private static final String driverClass = "com.mysql.cj.jdbc.Driver";
    private static final String url = "jdbc:mysql://127.0.0.1:3306/http?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String username = "http";
    private static final String password = "http.1234";
    private static final AtomicInteger atomicInteger = new AtomicInteger();//用于计数，增加了多少数据库连接

    private ConnectionFactory(){}

    static{
        init();
    }
    /**
     * 获取数据库连接
     * @return connection
     */
    public static Connection getConn(){
        Connection connection = connectionConcurrentLinkedQueue.poll();
        while(connection == null){
            if(logger.isDebugEnabled()){
                logger.debug("未获取到连接，重新获取一个");
            }
            instance.replenishConn();
            connection = connectionConcurrentLinkedQueue.poll();
        }
        return connection;
    }

    /**
     * 获取事务中的连接
     * @param id 线程id
     * @return 连接
     */
    public static Connection getConn(long id){
        return connectionConcurrentMap.get(id);
    }

    /**
     * 初始化
     */
    private static void init(){
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            if(logger.isErrorEnabled()){
                logger.error("数据库连接初始化失败: " + e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }
        instance.initConn(minKeep, false);

        //XWB-2020/9/18- 自动添加连接数
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int count = connectionConcurrentLinkedQueue.size();
                if(logger.isDebugEnabled()){
                    logger.debug("开始检查连接数，现在连接数： {}", count);
                }
                if(count < minKeep){
                    instance.initConn(maxKeep - count, true);
                }
            }
        }, 1, 60_000);
    }

    /**
     * 增加连接，每次一个
     */
    private void replenishConn(){
        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            connectionConcurrentLinkedQueue.add(connection);
            if(atomicInteger.incrementAndGet() >= 0){
                replenish = false;
            }
        } catch (SQLException throwable) {
            if(logger.isErrorEnabled()){
                logger.error("获取连接失败: " + throwable.getMessage(), throwable);
            }
        }
    }
    /**
     * 初始化连接
     * @param count 初始化连接个数
     * @param useThread 是否使用线程
     */
    private void initConn(int count, boolean useThread){
        if(replenish){
            return;
        }
        replenish = true;
        atomicInteger.set(-1 * count);
        if(connectionConcurrentLinkedQueue.size() > maxKeep){
            if(logger.isInfoEnabled()){
                logger.info("连接数超出最大值: {}, 现有: {}，不增加连接", maxKeep, connectionConcurrentLinkedQueue.size());
            }
        }else{
            if(logger.isDebugEnabled()){
                logger.debug("开始增加连接数： {}", count);
            }
            try{
                if(useThread){
                    for(int i=0;i<count;i++){
                        executorService.execute(this::replenishConn);
                    }
                }else{
                    for(int i=0;i<count;i++){
                        replenishConn();
                    }
                }
            }catch(Exception e){
                if(logger.isErrorEnabled()){
                    logger.error("增加连接数失败: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 将用完的连接放入缓存
     * @param connection 连接
     */
    public static void add(Connection connection){
        if(connection != null){
            connectionConcurrentLinkedQueue.add(connection);
        }
    }

    /**
     * 将连接添加为事务连接
     * @param id 线程id
     * @param connection 连接
     */
    public static void add(long id, Connection connection){
        if(connectionConcurrentMap.containsKey(id)){
            throw new RuntimeException("该线程已存在连接，不允许添加");
        }
        connectionConcurrentMap.put(id, connection);
    }

    /**
     * 删除线程中的连接，并且放入连接池
     * @param id 线程id
     */
    public static void close(long id){
        Connection connection = connectionConcurrentMap.remove(id);
        add(connection);
    }
    /**
     * 关闭ps
     * @param preparedStatement ps
     */
    public static void close(PreparedStatement preparedStatement){
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
    public static void close(PreparedStatement preparedStatement, ResultSet resultSet){
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
