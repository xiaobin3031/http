package com.xiaobin.http;

import com.xiaobin.conn.ConnectionFactory;
import com.xiaobin.sql.Dao;
import com.xiaobin.util.CodeKit;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * http遍历网页
 *
 * //todo 遍历网页，然后解析手机号明文
 * //todo 将http返回的错误信息存入数据库，需要新建字段
 *
 * //todo 主键改成自增，先有的主键改成uuid
 */
public class HttpCollect {

    private static final Logger logger = LoggerFactory.getLogger(HttpCollect.class);
    private static volatile boolean started = false;
    private HttpCollect(){}

    private static final HttpCollect instance = new HttpCollect();

    private static final Dao dao = new Dao();

    private static boolean timeoutFlag = false;//超时标志
    private static final int sleepBaseTime = 60;//超时时休息的最短时间，秒
    private static int sleepTime;
    private static volatile boolean counting = false; //是否已经在计秒
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public static void start(){
        if(started){
            return;
        }
        started = true;

    }

    /**
     * 执行查找
     */
    private void fetchHost() {
        insertHostDetail("https://www.hao123.com/",
                UUID.randomUUID().toString().replaceAll("-", ""),
                -1, "https://www.hao123.com/", -1, 0, 0, System.currentTimeMillis());
        //XWB-2020/8/11- 查询待处理的网址
        String sql = "select * from http_host_detail where state = 0 limit 0, 5000";
        List<Map<String, Object>> urlMapList = dao.findList(sql);
        while(!urlMapList.isEmpty()){
            urlMapList.parallelStream().filter(m -> dao.exec("update http_host_detail set state = 1 where state = 0 and id = ?", m.get("id")) != 0)
                    .forEach(map -> {
                        if(timeoutFlag){
                            try {
                                Thread.sleep(sleepTime * 1000);
                            } catch (InterruptedException e) {
                                if(logger.isErrorEnabled()){
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }
                        String uuid = (String)map.get("id");
                        String urlString = (String)map.get("url");
                        int level = (Integer)map.get("level");
                        int state = CodeKit.COMPLETE;
                        HttpURLConnection urlConnection = null;
                        try{
                            URL url = new URL(urlString);
                            urlConnection = (HttpURLConnection) url.openConnection();
                            urlConnection.setConnectTimeout(30_000);
                            urlConnection.setReadTimeout(15_000);
                            urlConnection.setRequestMethod("GET");
                            urlConnection.setDoInput(true);
                            urlConnection.setDoOutput(false);
                            urlConnection.connect();
                            int responseCode = urlConnection.getResponseCode();
                            dao.exec("update http_host_detail set url_state = ? where id = ?", responseCode, uuid);
                            if(responseCode >= 200 && responseCode < 300){
                                String msg = getContent(urlConnection.getInputStream());
                                urlConnection.disconnect();
                                if (!Strkit.isEmpty(msg)) {
                                    analysisName(msg, uuid);
                                    //analysisDigit(msg, uuid);
                                    analysisUrl(msg, uuid,level + 1);
                                }
                                state = CodeKit.COMPLETE;
                            }else{
                                if(logger.isInfoEnabled()){
                                    logger.info("{}: 返回: {}", urlString, urlConnection.getResponseMessage());
                                }
                                state = CodeKit.PART_COMPLETE;
                            }
                        }catch(IOException e){
                            if(logger.isErrorEnabled()){
                                logger.error("url: {}, exception: {}", urlString, e.getMessage(), e);
                            }
                            state = CodeKit.ERROR;
                            if(e instanceof java.net.SocketTimeoutException){
                                state = CodeKit.TIMEOUT;
                                timeoutFlag = true;
                                //toStopWhenTimeout();
                            }
                        } finally{
                            if(urlConnection != null){
                                urlConnection.disconnect();
                            }
                            dao.exec("update http_host_detail set state = ? where id = ?", state, uuid);
                        }
                    });
            urlMapList = dao.findList(sql);
        }
    }

    /**
     * 如果访问超时，则休息一定时间
     * Exception: java.net.SocketTimeoutException
     */
    private void toStopWhenTimeout(){
        if(counting){
            return;
        }
        counting = true;
        sleepTime = sleepBaseTime + secureRandom.nextInt(30);
        if(logger.isDebugEnabled()){
            logger.debug("因为访问页面超时，程序休息[{}]秒", sleepTime);
        }
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(logger.isDebugEnabled()){
                    logger.debug("{}", sleepTime);
                }
                sleepTime--;
                if(sleepTime <= 0){
                    timeoutFlag = false;
                    counting = false;
                    timer.cancel();
                }
            }
        }, 0, 1000);
    }
    /**
     * 分析部分的路径
     * @param msg 页面
     * @param uuid uuid
     */
    private void analysisName(String msg, String uuid){
        Pattern namePattern = Pattern.compile("<title>(.+?)</title>");
        Matcher matcher = namePattern.matcher(msg);
        String name = "";
        if(matcher.find()){
            name = matcher.group(1);
        }
        if(!Strkit.isEmpty(name)){
            if(name.matches("^.*[\\u4e00-\\u9fa5].*$")){
                dao.exec("update http_host_detail set name_c = ? where id = ?", name, uuid);
            }else{
                dao.exec("update http_host_detail set name_e = ? where id = ?", name, uuid);
            }
        }
    }
    /**
     * 分析数字串
     * @param msg 页面
     * @param uuid uuid
     */
    private void analysisDigit(String msg, String uuid){
        Pattern telPattern = Pattern.compile("(['\"])(?<=[^\\d])([\\d\\s-*]{11,20})(?=[^\\d])\\1");
        Matcher matcher = telPattern.matcher(msg);
        Set<String> telSet = new HashSet<>();
        while(matcher.find()){
            telSet.add(matcher.group(2));
        }
        if(!telSet.isEmpty()){
            String digit = telSet.stream().map(tel -> tel.replaceAll("[\\s-]+", "")).collect(Collectors.joining(","));
            dao.exec("insert into url_digit(id, digit) values(?, ?)", uuid, digit);
        }
    }

    /**
     * 解析url路径
     * @param msg
     * @param uuid
     */
    private void analysisHref(String msg, String uuid){

    }
    /**
     * 解析url
     * @param msg 上一次请求的返回内容
     * @param level 等级
     */
    private void analysisUrl(String msg, String uuid, int level) {
        Pattern urlPatter = Pattern.compile("(['\"])((?i)https?://[^/\"']+(?:/|.*?)?)\\1");
        Matcher matcher = urlPatter.matcher(msg);
        Set<String> urlList = new HashSet<>();
        while(matcher.find()){
            urlList.add(matcher.group(2));
        }
        if(!urlList.isEmpty()){
            for(String url: urlList){
                insertHostDetail(url,
                        UUID.randomUUID().toString().replaceAll("-", ""),
                        uuid, url, -1, level, 0, System.currentTimeMillis());
            }
        }
    }

    private int getPort(URL url){
        int port = url.getPort();
        if(port == -1){
            port = url.getDefaultPort();
        }
        return port;
    }
    private void insertHostDetail(String url, Object... objects){
        if(!dao.exist("select 1 from http_host_detail where url = ?", url)){
            dao.exec("insert into http_host_detail(id, parent_id, url, url_state, level, state, create_time) values(?,?,?,?,?,?,?)", objects);
        }
    }
    private String getContent(InputStream inputStream){
        StringBuilder stringBuilder = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))){
            String line;
            while((line = reader.readLine()) != null)
                stringBuilder.append(line);
        }catch(IOException e){
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
        }
        return stringBuilder.toString();
    }

    public static void main(String[] args) {
//        executorService.execute(instance::fetchHost);//收集网页url
//        executorService.execute(HttpHostCollect::start);//收集网页host和ip

        System.out.println(instance.allowedProtocolInJvm());
    }

    /**
     * 测试并返回支持的协议
     * @return 支持的协议列表
     */
    public List<String> allowedProtocolInJvm(){
        List<String> list = Arrays.asList(
            "http://www.baidu.com",//超文本传输协议
            "https://www.amazon.com/exec/obidos/order2/",//安全http
            "ftp://ibiblio.org/pub/languages/java/javafaq/",//文件传输协议
            "mailto://elharo@ibiblio.org",//简单邮件传输协议
            "telnet://dibner.poly.edu",//telnet
            "file:///etc/passwd",//本地文件访问
            "gopher://gopher.anc.org.za/",//gopher
            "ldap://ldap.itd.umich.edu/o=University%20of%20Michigan",//轻量组目录访问协议
            "jar:http://cafeaulait.org/books/javaio/ioexamples/javaio.jar!/com/macfaq/io/StreamCopier.class",//jar
            "nfs://utopia.poly.edu/usr/tmp",//NFS，网络文件系统
            "jdbc:mysql://luna.ibiblio.org:3306/NEWS",//jdbc的定制协议
            "rmi://ibiblio.org/RenderEngine",//rmi，远程方法调用的定制协议
            "doc:/UsersGuide/release.html",//HotJava的定制协议
            "netdoc:/UsersGuide/release.html",//HotJava的定制协议
            "systemresource://www.adc.org/+/index.html",//HotJava的定制协议
            "verbatim:http://www.adc.org/"//HotJava的定制协议
        );
        return list.stream().map(this::testProtocol).filter(Objects::nonNull).collect(Collectors.toList());
    }
    /**
     * 测试java虚拟机支持的协议
     * @param urlString url
     * @return 支持的协议，null为不支持
     */
    private String testProtocol(String urlString){
        try {
            URL url = new URL(urlString);
            if(logger.isDebugEnabled()){
                logger.debug("protocol:{} is supported", url.getProtocol());
            }
            return url.getProtocol();
        } catch (MalformedURLException e) {
            if(logger.isErrorEnabled()){
                logger.error("protocol:{} is not supported", urlString.substring(0, urlString.indexOf(":")));
            }
            return null;
        }
    }
}
