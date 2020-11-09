package com.xiaobin.collect.http;

import com.xiaobin.collect.MainCollect;
import com.xiaobin.model.Constant;
import com.xiaobin.model.NetworkUri;
import com.xiaobin.sql.Page;
import com.xiaobin.util.CharsetKit;
import com.xiaobin.util.CodeKit;
import com.xiaobin.util.ConstKit;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final byte[] CHARSET_BYTE_ARRAY = new byte[]{'c', 'h', 'a', 'r', 's', 'e', 't'};
    private static final int LENGTH = CHARSET_BYTE_ARRAY.length;

    private static final Map<String, String> protocolTransfer = new HashMap<>();

    private volatile boolean started = false;

    private final static HttpCollect instance = new HttpCollect();

    private HttpCollect(){}

    public static HttpCollect getInstance() {
        return instance;
    }

    private final List<String> ignoredProtocol = new ArrayList<>();

    static{
        protocolTransfer.put("hthttp", "http");
    }

    /**
     * 获取http连接
     * @param url url
     * @return http连接
     */
    private HttpURLConnection getConnection(URL url){
        try{
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setReadTimeout(15_000);
            httpURLConnection.setConnectTimeout(10_000);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(false);
            return httpURLConnection;
        }catch(IOException ioException){
            throw new RuntimeException(ioException);
        }
    }

    private Page<NetworkUri> pageList(int start, int end){
        NetworkUri networkUri = new NetworkUri();
        networkUri.setStatus(CodeKit.INIT);
        return networkUri.page(start, end);
    }

    private boolean isCharset(byte b, char c){
        return b == '-' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * 获取网页上的编码，content-type中，如果mime后面有空格，就无法通过HttpURLConnection.getContentType读取到编码
     * @param bytes 网页字节数组
     * @return 编码，默认使用UTF-8
     */
    private String getCharset(byte[] bytes){
        int i = 0, index = 0;
        for(; i<= bytes.length - LENGTH && index < LENGTH; i++){
            if(bytes[i] == CHARSET_BYTE_ARRAY[index]){
                index++;
            }else{
                index = 0;
            }
        }
        if(i == bytes.length - LENGTH){
            return StandardCharsets.UTF_8.toString();
        }else{
            int length = i + 50, firstIndex = 0, endIndex = 0;
            byte b;
            char c;
            for(; i < length; i++){
                b = bytes[i];
                c = (char) b;
                if(isCharset(b, c)){
                    if(firstIndex == 0){
                        firstIndex = i;
                    }
                }else{
                    if(firstIndex > 0){
                        endIndex = i;
                        break;
                    }
                }
            }
            byte[] tmp = new byte[endIndex - firstIndex];
            System.arraycopy(bytes, firstIndex, tmp, 0, tmp.length);
            return CharsetKit.getCharset(new String(tmp, StandardCharsets.UTF_8));
        }
    }

    /**
     * uri中是否带协议
     * @param uri uri
     * @return true：带协议，false：不带协议
     */
    private boolean isWithSchema(String uri){
        return uri.matches("^[^:]+:.+$");
    }

    /**
     * 校验协议是否无效
     * @param url url
     * @return 校验结果，true：无效，false：有效
     */
    private boolean invalidScheme(URL url){
        return !this.ignoredProtocol.isEmpty() && this.ignoredProtocol.contains(url.getProtocol());
    }

    /**
     * 校验协议是否无效
     * @param uri uri
     * @return 校验结果，true：无效，false：有效
     */
    private boolean invalidScheme(URI uri){
        return !this.ignoredProtocol.isEmpty() && this.ignoredProtocol.contains(uri.getScheme());
    }
    /**
     * 解析返回的信息
     * @param bytes 网页信息
     * @param id 数据id
     * @param topParentId 顶级父类
     * @param url url
     * @param level 等级
     */
    private void withResponse(byte[] bytes, int id, int topParentId, URL url, int level){
        String charset = getCharset(bytes);
        String msg;
        try {
            msg = new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
            return;
        }
        Pattern pattern = Pattern.compile("<title>(.+?)</title>");
        Matcher matcher = pattern.matcher(msg);
        String title = null;
        NetworkUri networkUri = new NetworkUri();
        networkUri.setId(id);
        if(matcher.find()){
            title = matcher.group(1);
            networkUri.setTitle(title);
        }
        networkUri.setCharset(charset);
        networkUri.update();
        //XWB-2020/9/21- 解析路径
        pattern = Pattern.compile("<a[^>]+href=['\"]?([^'\"\\s><]+)[^><]*>");
        matcher = pattern.matcher(msg);
        NetworkUri tmp = new NetworkUri();
        List<String> uriList = new ArrayList<>();
        while(matcher.find()){
            String string = matcher.group(1);
            if(uriList.contains(string)){
                continue;
            }
            uriList.add(string);
            networkUri = new NetworkUri();
            networkUri.setLevel(level + 1);
            try{
                if (isWithSchema(string)) {
                    if(string.toLowerCase().startsWith("http")){
                        URL tmpUrl = new URL(string);
                        if(this.invalidScheme(tmpUrl)){
                            continue;
                        }
                        withHttpUrl(tmpUrl, networkUri);
                        if(Strkit.isEmpty(tmpUrl.getFile())){
                            //XWB-2020/9/29- 将没有子目录的url的级别设置成0
                            networkUri.setLevel(0);
                        }
                    }else{
                        URI uri = new URI(string);
                        if(this.invalidScheme(uri)){
                            continue;
                        }
                        //todo 替换协议
                        networkUri.setUri(uri.toString());
                        networkUri.setProtocol(uri.getScheme());
                        networkUri.setStatus(CodeKit.COMPLETE);//如果不是http，就直接完成
                        //XWB-2020/9/22- 完成时，将当前页的标题赋值给它
                        networkUri.setTitle(title);
                    }
                }else{
                    URL tmpUrl = new URL(url, string);
                    withHttpUrl(tmpUrl, networkUri);
                }
            } catch (MalformedURLException | URISyntaxException e) {
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
                networkUri.setUri(string);
                networkUri.setStatus(CodeKit.ERROR);
                networkUri.setMessage(e.getMessage());
            }
            tmp.setUri(networkUri.getUri());
            if(tmp.find().isEmpty()){
                networkUri.setParentId(id);
                if(topParentId == 0){
                    networkUri.setTopParentId(id);
                }else{
                    networkUri.setTopParentId(topParentId);
                }

                networkUri.insert();
            }
        }
        networkUri = new NetworkUri();
        networkUri.setId(id);
        networkUri.setStatus(CodeKit.COMPLETE);
        networkUri.update();
    }

    private void withHttpUrl(URL url, NetworkUri networkUri){
        networkUri.setProtocol(url.getProtocol());
        networkUri.setUri(url.getProtocol() + "://" + url.getAuthority() + url.getFile());
        if(url.getHost().endsWith(".gov")){
            //XWB-2020/9/21- 如果是政府网站，直接完成
            networkUri.setStatus(CodeKit.GOV_NET);
        }
    }

    @Override
    public void start(){
        if(this.started){
            return;
        }
        if(logger.isDebugEnabled()){
            logger.debug("启动手机类: {}", HttpCollect.class.getName());
        }
        //@Date:2020/11/9 - @Author:XWB - @Msg: 获取不正常的协议名称，以便过滤
        Constant constant = new Constant();
        constant.setType(ConstKit.HTTP_COLLECT_PROTOCOL_IGNORE);
        List<Constant> constantList = constant.find();
        if(!constantList.isEmpty()){
            this.ignoredProtocol.addAll(constantList.stream().map(Constant::getValue).filter(s -> !Strkit.isEmpty(s))
                .flatMap(s -> Arrays.stream(s.split(","))).collect(Collectors.toList()));
        }
        this.started = true;
        int start = 0, size = 100;
        while(true){
            Page<NetworkUri> page = pageList(start, start + size);
            if(page.getTotal() == 0){
                if(start > 0){
                    if(logger.isInfoEnabled()){
                        logger.info("返回的数据为空，但start 大于0，重置成0后，重新查询");
                    }
                    start = 0;
                    continue;
                }
                if(logger.isInfoEnabled()){
                    logger.info("表network_uri中再无status=init数据，退出");
                }
                break;
            }
            List<NetworkUri> networkUriList = page.getList();
            for(NetworkUri networkUri: networkUriList){
                NetworkUri newNetworkUri = new NetworkUri();
                newNetworkUri.setId(networkUri.getId());
                newNetworkUri.setStatus(CodeKit.FETCHING);
                if(newNetworkUri.update() != 1){
                    if(logger.isDebugEnabled()){
                        logger.debug("{} 已经在处理", networkUri.getUri());
                    }
                    continue;
                }
                HttpURLConnection httpURLConnection = null;
                try{
                    URL url = new URL(networkUri.getUri());
                    httpURLConnection = getConnection(url);
                    httpURLConnection.connect();

                    int code = httpURLConnection.getResponseCode();
                    newNetworkUri.setHttpCode(code);
                    newNetworkUri.setServer(httpURLConnection.getHeaderField("Server"));
                    newNetworkUri.setContentLength(httpURLConnection.getHeaderFieldLong("Content-Length", 0));
                    String contentType = httpURLConnection.getHeaderField("Content-Type");
                    newNetworkUri.setContentType(contentType);
                    if(code == 200){
                        if(contentType.contains("text/")){
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            byte[] bytes = new byte[1024];
                            while(httpURLConnection.getInputStream().read(bytes) != -1){
                                byteArrayOutputStream.write(bytes);
                            }
                            withResponse(byteArrayOutputStream.toByteArray(), networkUri.getId(),
                                    networkUri.getTopParentId(), url, networkUri.getLevel());
                        }else{
                            //XWB-2020/9/21- 如果不是text/，就直接完成
                            newNetworkUri.setStatus(CodeKit.COMPLETE);
                        }
                    }else{
                        newNetworkUri.setMessage(httpURLConnection.getResponseMessage());
                        networkUri.setStatus(CodeKit.COMPLETE);
                    }
                }catch(Exception ioException){
                    if(logger.isErrorEnabled()){
                        logger.error("uri: {}", networkUri.getUri(), ioException);
                    }
                    newNetworkUri.setStatus(CodeKit.ERROR);
                    newNetworkUri.setMessage(ioException.getMessage());
                } finally{
                    if(httpURLConnection != null){
                        httpURLConnection.disconnect();
                    }
                }
                newNetworkUri.update();
            }
            start += size;
        }
    }

    public static void main(String[] args) {
        HttpCollect main = new HttpCollect();
        main.start();
    }
}
