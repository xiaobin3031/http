package com.xiaobin.collect.http;

import com.xiaobin.collect.MainCollect;
import com.xiaobin.model.NetworkUri;
import com.xiaobin.sql.Page;
import com.xiaobin.sql.SqlFactory;
import com.xiaobin.util.CodeKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * http搜集
 *
 * header
 * 0 = "Accept-Ranges"
 * 2 = "Server"
 * 3 = "ETag"
 * 4 = "Content-Location"
 * 5 = "Last-Modified"
 * 6 = "Content-Length"
 * 7 = "Date"
 * 8 = "Content-Type"
 */
public class HttpCollect extends MainCollect {

    private static final Logger logger = LoggerFactory.getLogger(HttpCollect.class);

    private volatile boolean started = false;
    private final static int MAX_SIZE = 100;

    private final static HttpCollect instance = new HttpCollect();

    //多出来的一个线程用来执行sql
    private final static ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_SIZE + 1);

    static {
        executorService.setRejectedExecutionHandler((r, executor) -> {
            if(logger.isTraceEnabled()){
                logger.trace("some thread is rejected, {}", r);
            }
        });
    }
    private HttpCollect(){}

    public static HttpCollect getInstance() {
        return instance;
    }

    private Page<NetworkUri> pageList(){
        NetworkUri networkUri = new NetworkUri();
        networkUri.setStatus(CodeKit.INIT);
        return SqlFactory.page(networkUri, 0, MAX_SIZE);
    }

    @Override
    public void start(){
        if(this.started){
            return;
        }
        if(logger.isDebugEnabled()){
            logger.debug("start collect class: {}", HttpCollect.class.getName());
        }
        this.started = true;
        List<Future<Object>> futureList = new ArrayList<>();
        Future<Page<NetworkUri>> future = null;//使待遍历数据更快速的就绪
        Page<NetworkUri> page = pageList();
        while(true){
            if(future != null){
                page = futureGet(future);
            }
            if(page == null || page.getTotal() == 0){
                if(logger.isInfoEnabled()){
                    logger.info("there is no data of status is init in table network_uri");
                }
                break;
            }
            List<NetworkUri> networkUriList = page.getList();
            futureList.clear();
            for(NetworkUri networkUri: networkUriList){
                NetworkUri newNetworkUri = new NetworkUri();
                newNetworkUri.setId(networkUri.getId());
                newNetworkUri.setStatus(CodeKit.FETCHING);
                if(logger.isTraceEnabled()){
                    logger.trace("{} begin to process id", networkUri.getId());
                }
                if(SqlFactory.update(newNetworkUri) != 1){
                    if(logger.isDebugEnabled()){
                        logger.debug("{} is processing, skip", networkUri.getUri());
                    }
                    continue;
                }
                futureList.add(executorService.submit(new HttpCollectCallable(networkUri)));
                if(logger.isTraceEnabled()){
                    logger.trace("{} add id in executorService", networkUri.getId());
                }
            }
            future = executorService.submit(this::pageList);
            if(!futureList.isEmpty()){
                futureList.forEach(this::futureGet);
            }
        }
    }

    private <T> T futureGet(Future<T> future){
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }

    public static void main(String[] args) {
        HttpCollect main = new HttpCollect();
        main.start();
    }
}
