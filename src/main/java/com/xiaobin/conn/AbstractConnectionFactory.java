package com.xiaobin.conn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连接父类
 * @param <T> 连接对象
 */
public abstract class AbstractConnectionFactory<T extends ConnectionObj> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractConnectionFactory.class);
    private final ConcurrentLinkedQueue<T> connectionConcurrentLinkedQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentMap<Long, T> connectionConcurrentMap = new ConcurrentHashMap<>();
    private volatile boolean replenish = false;//是否正在添加连接
    private final AtomicInteger atomicInteger = new AtomicInteger();//用于计数，增加了多少数据库连接
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ConnectionConfig connectionConfig = new ConnectionConfig();

    protected void setMaxKeep(int maxKeep) {
        this.connectionConfig.setMaxKeep(maxKeep);
    }

    protected void setMinKeep(int minKeep) {
        this.connectionConfig.setMinKeep(minKeep);
    }

    protected void setInactiveTime(int inactiveTime){
        this.connectionConfig.setInactiveTime(inactiveTime);
    }

    /**
     * 子类初始化，必须由子类去实现
     */
    protected abstract void init();
    /**
     * 初始化参数
     */
    protected final void _init(){
        init();

        initConn((connectionConfig.getMaxKeep() - connectionConfig.getMinKeep()) / 2, false);
        //XWB-2020/9/18- 自动添加连接数
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int count = connectionConcurrentLinkedQueue.size();
                if(logger.isDebugEnabled()){
                    logger.debug("开始检查连接数，现在连接数： {}", count);
                }
                if(count < connectionConfig.getMinKeep()){
                    initConn(connectionConfig.getMaxKeep() - count, true);
                }else if(count > connectionConfig.getMaxKeep()){
                    while(connectionConcurrentLinkedQueue.size() > connectionConfig.getMaxKeep()){
                        close(getConn());
                    }
                }
            }
        }, 60_000, 60_000);

        //XWB-2020/9/22- 为过时的connection执行sql
        Timer activeTimer = new Timer();
        activeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(logger.isDebugEnabled()){
                    logger.debug("开始为过时的connection执行刷新方法");
                }
                int length = connectionConcurrentLinkedQueue.size();
                if(length > 0){
                    long current = System.currentTimeMillis();
                    for(int i=0;i< length;i++){
                        T t = connectionConcurrentLinkedQueue.poll();
                        if(t != null){
                            if(current - t.getLastUseTime() > connectionConfig.getInactiveTime()){
                                try{
                                    refreshConn(t);
                                    connectionConcurrentLinkedQueue.add(t);
                                }catch(Exception e){
                                    if(logger.isErrorEnabled()){
                                        logger.error("刷新连接失败: {}", e.getMessage(), e);
                                    }
                                }
                            }
                        }
                    }
                    length = connectionConcurrentLinkedQueue.size();
                    if(length < connectionConfig.getMinKeep()){
                        initConn((connectionConfig.getMinKeep() - length) / 2 + length, true);
                    }
                }
            }
        }, connectionConfig.getInactiveTime() / 2, connectionConfig.getInactiveTime() / 2);
    }

    protected abstract T getSingleConn() throws Exception;

    /**
     * 刷新一个连接
     * @param t t
     */
    protected abstract void refreshConn(T t) throws Exception;
    /**
     * 增加连接
     */
    private void replenishConn(){
        try{
            T t = getSingleConn();
            if(t != null){
                connectionConcurrentLinkedQueue.add(t);
                if(this.atomicInteger.incrementAndGet() >= 0){
                    this.replenish = false;
                }
            }
        }catch(Exception e){
            if(logger.isErrorEnabled()){
                logger.error("增加连接失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 初始化连接
     * @param count 初始化连接个数
     * @param useThread 是否使用线程
     */
    private void initConn(int count, boolean useThread){
        if(count <= 0){
            if(logger.isWarnEnabled()){
                logger.warn("需要增加的连接数异常: {}", count);
            }
            return;
        }
        if(this.replenish){
            if(logger.isWarnEnabled()){
                logger.warn("连接正在增加中，剩余：{}", Math.abs(atomicInteger.get()));
            }
            return;
        }
        this.replenish = true;
        this.atomicInteger.set(-1 * count);
        if(this.connectionConcurrentLinkedQueue.size() > this.connectionConfig.getMaxKeep()){
            if(logger.isInfoEnabled()){
                logger.info("连接数超出最大值: {}, 现有: {}，不增加连接", this.connectionConfig.getMaxKeep(), this.connectionConcurrentLinkedQueue.size());
            }
        }else{
            if(logger.isDebugEnabled()){
                logger.debug("开始增加连接数： {}", count);
            }
            try{
                if(useThread){
                    for(int i=0;i<count;i++){
                        this.executorService.execute(this::replenishConn);
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
     * 关闭对象
     * @param t 对象
     */
    protected abstract void close(T t);
    /**
     * 获取连接对象
     * @return 连接对象
     */
    public T getConn(){
        T t = this.connectionConcurrentLinkedQueue.poll();
        while(t == null){
            if(logger.isDebugEnabled()){
                logger.debug("未获取到连接，重新获取一个");
            }
            replenishConn();
            t = connectionConcurrentLinkedQueue.poll();
            initConn((this.connectionConfig.getMaxKeep() - this.connectionConfig.getMinKeep()) / 2 + this.connectionConfig.getMinKeep(), true);
        }
        return t;
    }

    /**
     * 获取事务中的连接
     * @param id 线程id
     * @return 连接
     */
    public T getConn(long id){
        return connectionConcurrentMap.get(id);
    }

    /**
     * 将用完的连接放入缓存
     * @param t 连接
     */
    public void inPool(T t){
        if(t != null){
            this.connectionConcurrentLinkedQueue.add(t);
            t.setLastUseTime();
        }
        if(logger.isDebugEnabled()){
            logger.debug("当前连接数: " + connectionConcurrentLinkedQueue.size());
        }
    }

    /**
     * 将连接添加为事务连接
     * @param id 线程id
     * @param t 连接
     */
    public void inThread(long id, T t){
        if(this.connectionConcurrentMap.containsKey(id)){
            throw new RuntimeException("该线程已存在连接，不允许添加");
        }
        connectionConcurrentMap.put(id, t);
    }

    /**
     * 删除线程中的连接，并且放入连接池
     * @param id 线程id
     */
    public void removeFromThread(long id){
        T t = connectionConcurrentMap.remove(id);
        inPool(t);
    }
}
