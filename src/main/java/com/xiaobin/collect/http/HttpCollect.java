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
    private final static int MAX_SIZE = 1;

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
        List<Future<List<NetworkUri>>> futureList = new ArrayList<>();
        List<NetworkUri> networkUriAddList = new ArrayList<>();
        Future<Object> sqlFuture = null;//只是为了有个堵塞的效果
        while(true){
            Page<NetworkUri> page = pageList();
            if(page.getTotal() == 0){
                if(sqlFuture != null){
                    try {
                        sqlFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        if(logger.isErrorEnabled()){
                            logger.error(e.getMessage(), e);
                        }
                    }
                    sqlFuture = null;
                    continue;
                }
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
            networkUriAddList.clear();
            if(!futureList.isEmpty()){
                futureList.stream().map(f -> {
                    try {
                        return f.get();
                    } catch (InterruptedException | ExecutionException e) {
                        if(logger.isErrorEnabled()){
                            logger.error(e.getMessage(), e);
                        }
                        return null;
                    }
                }).filter(l -> l != null && !l.isEmpty()).forEach(networkUriAddList::addAll);
            }
            if(!networkUriAddList.isEmpty()){
                sqlFuture = executorService.submit(() -> {
                    SqlFactory.insertList(networkUriAddList.get(0), networkUriAddList);
                    return null;
                });
            }
        }
    }

    public static void main(String[] args) {
        HttpCollect main = new HttpCollect();
        main.start();
    }
}
