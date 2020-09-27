package com.xiaobin.conn.redis;

import com.xiaobin.conn.AbstractConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * redis的连接配置
 */
public class RedisConnection extends AbstractConnectionFactory<RedisObj> {

    private final static Logger logger = LoggerFactory.getLogger(RedisConnection.class);

    private static final RedisConnection instance = new RedisConnection();

    private static final RedisConfig redisConfig = new RedisConfig();

    private RedisConnection(){}

    public static RedisConnection getInstance() {
        return instance;
    }

    static{
        instance._init();
    }

    @Override
    protected void init() {
        redisConfig.setHost("127.0.0.1");
        redisConfig.setPort(6379);

        setMinKeep(1);
        setMaxKeep(3);
    }

    @Override
    protected RedisObj getSingleConn() throws Exception {
        Socket socket = new Socket(redisConfig.getHost(), redisConfig.getPort());
        socket.setSoTimeout(10_000);
        socket.setTcpNoDelay(true);//尽可能块的发送，而不论包大小
        socket.setSoLinger(true, 0);//close时，如何处理未发送的数据包，此设置下，立即丢弃

        return new RedisObj(socket);
    }

    @Override
    protected void refreshConn(RedisObj redisObj) throws Exception {

    }

    @Override
    public void close(RedisObj redisObj) {
        if(redisObj.getSocket() != null){
            try {
                redisObj.getSocket().close();
            } catch (IOException ioException) {
                if(logger.isErrorEnabled()){
                    logger.error("关闭redis的socket失败： {}", ioException.getMessage(), ioException);
                }
            }
        }
    }
}
