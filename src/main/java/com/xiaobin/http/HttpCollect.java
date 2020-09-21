package com.xiaobin.http;

import com.xiaobin.sql.Page;
import com.xiaobin.sql.model.NetworkUri;
import com.xiaobin.util.CodeKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class HttpCollect {

    private static final Logger logger = LoggerFactory.getLogger(HttpCollect.class);

    private static final Executor executor = Executors.newCachedThreadPool();

    private static final byte[] CHARSET_BYTE_ARRAY = new byte[]{'c', 'h', 'a', 'r', 's', 'e', 't'};
    private static final int LENGTH = CHARSET_BYTE_ARRAY.length;
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

    private Page<NetworkUri> uriList(int start, int end){
        NetworkUri networkUri = new NetworkUri();
        networkUri.setStatus(CodeKit.INIT);
        return networkUri.page(start, end);
    }

    private String getCharset(byte[] bytes){
        byte[] tmp = new byte[LENGTH];
        int index = 0;
        while (!Arrays.equals(CHARSET_BYTE_ARRAY, tmp) && index + LENGTH < bytes.length) {
            System.arraycopy(bytes, index, tmp, 0, LENGTH);
            index++;
        }
        if(index + LENGTH >= bytes.length){
            return "UTF-8";
        }else{
            while(bytes[index] != '='){
                index++;
            }
            int firstIndex = ++index;
            byte b;
            char c;
            do{
                b = bytes[index++];
                c = (char) b;
            }while(b == '-' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'));
            int endIndex = index - 1;
            tmp = new byte[endIndex - firstIndex];
            System.arraycopy(bytes, firstIndex, tmp, 0, tmp.length);
            return new String(tmp, StandardCharsets.UTF_8);
        }
    }
    /**
     * 解析返回的信息
     * @param bytes 网页信息
     * @param id 数据id
     * @param url url
     * @param level 等级
     */
    private void withResponse(byte[] bytes, int id, URL url, int level){
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
        if(matcher.find()){
            NetworkUri networkUri = new NetworkUri();
            networkUri.setId(id);
            networkUri.setTitle(matcher.group(1));
            networkUri.update();
        }
        //XWB-2020/9/21- 解析连接，多种协议
        pattern = Pattern.compile("" +
                "(['\"])?" +
                "((?:https?|data|file|ftp|mailto|magnet|telnet|urn):['\"]+)" +
                "(?:\1)?");
        matcher = pattern.matcher(msg);
        while(matcher.find()){
            NetworkUri networkUri = new NetworkUri();
            networkUri.setLevel(level + 1);
            try {
                String string = matcher.group(2);
                if(string.startsWith("http")){
                    URL tmpUrl = new URL(string);
                    withHttpUrl(tmpUrl, networkUri);
                }else{
                    URI uri = new URI(string);
                    networkUri.setUri(uri.toString());
                    networkUri.setProtocol(uri.getScheme());
                    networkUri.setStatus(CodeKit.COMPLETE);//如果不是http，就直接完成
                }
            } catch (URISyntaxException | MalformedURLException e) {
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            }
            try{
                networkUri.insert();
            }catch(Exception e){
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            }
        }
        //XWB-2020/9/21- 解析相对路径
        pattern = Pattern.compile("(?:href|src)=(['\"])?([^:]+)(?:\1)?");
        matcher = pattern.matcher(msg);
        while(matcher.find()){
            String string = matcher.group(2);
            NetworkUri networkUri = new NetworkUri();
            networkUri.setLevel(level + 1);
            try{
                URL tmpUrl = new URL(url, string);
                withHttpUrl(tmpUrl, networkUri);
            } catch (MalformedURLException e) {
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            }
            try{
                networkUri.insert();
            }catch(Exception e){
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            }
        }
        NetworkUri networkUri = new NetworkUri();
        networkUri.setId(id);
        networkUri.setStatus(CodeKit.COMPLETE);
    }

    private void withHttpUrl(URL url, NetworkUri networkUri){
        networkUri.setProtocol(url.getProtocol());
        networkUri.setUri(url.getProtocol() + "://" + url.getAuthority() + url.getFile());
        if(url.getHost().endsWith(".gov")){
            //XWB-2020/9/21- 如果是政府网站，直接完成
            networkUri.setStatus(CodeKit.GOV_NET);
        }
    }
    private void start(){
        int start = 0, size = 100;
        while(true){
            Page<NetworkUri> page = uriList(start, start + size);
            if(page.getTotal() == 0){
                if(start > 0){
                    if(logger.isInfoEnabled()){
                        logger.info("返回的数据为空，但start 大于0，重制成0后，重新查询");
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
                try{
                    URL url = new URL(networkUri.getUri());
                    HttpURLConnection httpURLConnection = getConnection(url);
                    httpURLConnection.connect();

                    int code = httpURLConnection.getResponseCode();
                    networkUri.setStatus(code);
                    if(code == 200){
                        networkUri.setServer(httpURLConnection.getHeaderField("Server"));
                        networkUri.setContentLength(httpURLConnection.getHeaderFieldLong("Content-Length", 0));
                        String contentType = httpURLConnection.getHeaderField("Content-Type");
                        networkUri.setContentType(contentType);
                        if(contentType.contains("text/")){
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            byte[] bytes = new byte[1024];
                            while(httpURLConnection.getInputStream().read(bytes) != -1){
                                byteArrayOutputStream.write(bytes);
                            }
                            executor.execute(() -> withResponse(byteArrayOutputStream.toByteArray(), networkUri.getId(), url, networkUri.getLevel()));
                        }else{
                            //XWB-2020/9/21- 如果不是text/，就直接完成
                            networkUri.setStatus(CodeKit.COMPLETE);
                        }
                    }else{
                        networkUri.setMessage(httpURLConnection.getResponseMessage());

                    }
                    httpURLConnection.disconnect();
                    networkUri.update();
                }catch(IOException ioException){
                    if(logger.isErrorEnabled()){
                        logger.error(ioException.getMessage(), ioException);
                    }
                }
            }
            start += size;

            //todo 测试代码，只执行一次
            break;
        }
    }

    public static void main(String[] args) {
        HttpCollect httpCollect = new HttpCollect();
        httpCollect.start();
    }
}
