package com.xiaobin.http;

import com.xiaobin.sql.Dao2;
import com.xiaobin.util.CodeKit;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * 主机地址
 */
public class HttpHostCollect {

    private static final Logger logger = LoggerFactory.getLogger(HttpHostCollect.class);
    private static final HttpHostCollect INSTANCE = new HttpHostCollect();
    private HttpHostCollect(){}
    private static final String CONST_TYPE = "http.host.detail.create-time.node";
    private static final Dao2 DAO_2 = new Dao2();

    private static boolean detailEmpty = false;//detail表没有新的数据
    private static int emptyCount = 0;//detail表连续为空的次数
    private static final int EMPTY_MAX_COUNT = 5;//detail表连续为空的最大次数
    private static long sleepTime = 60;//休息时间，秒
    private static final long SLEEP_MAX_TIME = 600;//最大休息时间，秒

    static void start(){
        //XWB-2020/8/12- 查找时间节点
        if(DAO_2.findOne("select * from constant where type = ?", CONST_TYPE).isEmpty()){
            Map<String, Object> map = DAO_2.findOne("select min(create_time) as create_time from http_host_detail");
            if(map.get("create_time") == null){
                if(logger.isDebugEnabled()){
                    logger.debug("表中记录不存在");
                }
                return;
            }
            if(DAO_2.exec("insert into constant(type, value) values(?, ?)", CONST_TYPE, map.get("create_time")) == 0){
                if(logger.isErrorEnabled()){
                    logger.error("插入host的时间节点失败");
                }
                return;
            }
        }
        INSTANCE.fetch();
    }

    /**
     * 加入新的url
     */
    private void addNewHost(){
        String sql = "select * from http_host_detail where create_time > ? order by create_time limit 0, 1000";
        Map<String, Object> map = DAO_2.findOne("select * from constant where type = ?", CONST_TYPE);
        if(map.get("value") == null){
            if(logger.isInfoEnabled()){
                logger.info("{}为空", CONST_TYPE);
            }
            return;
        }
        List<Map<String, Object>> list = DAO_2.findList(sql, map.get("value"));
        if(list.size() > 0){
            emptyCount = 0;
            List<Map<String, Object>> paramsList = new ArrayList<>();
            List<String> urlList = new ArrayList<>();
            for(Map<String, Object> tmp: list){
                String url = (String)tmp.get("url");
                if(!Strkit.isEmpty(url)){
                    int index = url.indexOf("://");
                    if(index > -1){
                        String protocol = url.substring(0, index);
                        url = url.substring(index + 3);
                        index = url.indexOf("/");
                        if(index > -1){
                            url = url.substring(0, index);
                        }
                        if(urlList.contains(url)){
                            continue;
                        }
                        urlList.add(url);
                        Map<String, Object> param = new HashMap<>();
                        param.put("protocol", protocol);
                        param.put("host", url);
                        paramsList.add(param);
                    }
                }
            }
            if(!paramsList.isEmpty()){
                for(Map<String, Object> tmp: paramsList){
                    String host = (String) tmp.get("host");
                    if(!DAO_2.exist("select 1 from http_host where host = ?", host)){
                        DAO_2.exec("insert into http_host(id, host, protocol, port, url_state, create_time) values(?,?,?,?,?,?)"
                                , UUID.randomUUID().toString().replaceAll("-", "")
                                , host, tmp.get("protocol"), -1, -1, System.currentTimeMillis());
                    }
                }
            }
            DAO_2.exec("update constant set value = ? where type = ?", list.get(list.size() - 1).get("create_time"), CONST_TYPE);
        }else{
            emptyCount++;
            detailEmpty = true;
        }
    }

    /**
     * 遍历头
     */
    private void fetch(){
        while(true){
            List<Map<String, Object>> list = find();
            if(list.isEmpty()){
                if(emptyCount >= EMPTY_MAX_COUNT){
                    if(logger.isInfoEnabled()){
                        logger.info("detail表连续[{}]次为空，退出解析", emptyCount);
                    }
                    return;
                }
                if(detailEmpty){
                    //XWB-2020/8/19- 每次休息，都增加一分钟
                    if(sleepTime < SLEEP_MAX_TIME){
                        sleepTime += 60;
                    }
                    if(logger.isInfoEnabled()){
                        logger.info("已无更多数据, 休息[{}]秒", sleepTime);
                    }
                    try {
                        Thread.sleep(sleepTime * 1000);
                    } catch (InterruptedException e) {
                        if(logger.isErrorEnabled()){
                            logger.error(e.getMessage(), e);
                        }
                    }
                    detailEmpty = false;
                }
            }else{
                while(!list.isEmpty()){
                    for(Map<String, Object> map: list){
                        String id = (String)map.get("id");
                        String url = (String)map.get("host");
                        String address = "";
                        int status = CodeKit.COMPLETE;
                        try {
                            InetAddress inetAddress = InetAddress.getByName(url);
                            address = inetAddress.getHostAddress();
                        } catch (UnknownHostException e) {
                            if(logger.isErrorEnabled()){
                                logger.error("url: {}; {}", url, e.getMessage(), e);
                            }
                            status = CodeKit.UNKNOWN_HOST;
                        } catch(Exception e){
                            if(logger.isErrorEnabled()){
                                logger.error("url: {}; {}", url, e.getMessage(), e);
                            }
                            status = CodeKit.ERROR;
                        } finally{
                            DAO_2.exec("update http_host set ip = ?, state = ? where id = ?", address, status, id);
                        }
                    }
                    list = find();
                }
            }

            addNewHost();
        }
    }

    private List<Map<String, Object>> find(){
        return DAO_2.findList("select * from http_host where state = 0 limit 0, 500");
    }
}
