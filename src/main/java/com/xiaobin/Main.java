package com.xiaobin;

import com.xiaobin.collect.MainCollect;
import com.xiaobin.collect.http.HttpCollect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(MainCollect.class);

    private final static ConcurrentMap<String, MainCollect> mainCollectConcurrentMap = new ConcurrentHashMap<>();

    /**
     * 注册启动信息
     */
    private static void register(){
        mainCollectConcurrentMap.put(HttpCollect.class.getName(), HttpCollect.getInstance());
    }

    public static void main(String[] args) {
        if(logger.isInfoEnabled()){
            logger.info("收集启动");
        }
        register();
        if(mainCollectConcurrentMap.isEmpty()){
            if(logger.isInfoEnabled()){
                logger.info("尚未注册收集子类，退出");
            }
        }else{
            ExecutorService executor = Executors.newFixedThreadPool(mainCollectConcurrentMap.size());
            mainCollectConcurrentMap.values().forEach(t -> executor.execute(t::start));
        }
    }
}
