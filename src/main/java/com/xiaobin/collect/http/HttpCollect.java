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
    private final static ExecutorService executorService = Executors.newFixedThreadPool(MAX_SIZE + 1);

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
            logger.debug("启动收集类: {}", HttpCollect.class.getName());
        }
        this.started = true;
        List<Future<Object>> futureList = new ArrayList<>();
        while(true){
            Page<NetworkUri> page = pageList();
            if(page.getTotal() == 0){
                if(logger.isInfoEnabled()){
                    logger.info("表network_uri中再无status=init数据，退出");
                }
                break;
            }
            List<NetworkUri> networkUriList = page.getList();
            futureList.clear();
            for(NetworkUri networkUri: networkUriList){
                NetworkUri newNetworkUri = new NetworkUri();
                newNetworkUri.setId(networkUri.getId());
                newNetworkUri.setStatus(CodeKit.FETCHING);
                if(SqlFactory.update(newNetworkUri) != 1){
                    if(logger.isDebugEnabled()){
                        logger.debug("{} 已经在处理", networkUri.getUri());
                    }
                    continue;
                }
                futureList.add(executorService.submit(new HttpCollectCallable(networkUri)));
            }
            if(!futureList.isEmpty()){
                futureList.forEach(f -> {
                    try {
                        long start = System.currentTimeMillis();
                        f.get();
                        long end = System.currentTimeMillis();
                        long sep = start - end;
                        System.out.println(sep);
                    } catch (InterruptedException | ExecutionException e) {
                        if(logger.isErrorEnabled()){
                            logger.error(e.getMessage(), e);
                        }
                    }
                });
            }
        }
    }

    public static void main(String[] args) {
        HttpCollect main = new HttpCollect();
        main.start();
    }
}
