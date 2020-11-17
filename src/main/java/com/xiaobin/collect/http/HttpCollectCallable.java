package com.xiaobin.collect.http;

import com.xiaobin.model.Constant;
import com.xiaobin.model.NetworkUri;
import com.xiaobin.sql.SqlFactory;
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
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * http收集类的执行动作
 */
public class HttpCollectCallable implements Callable<List<NetworkUri>> {
    private static final Logger logger = LoggerFactory.getLogger(HttpCollectCallable.class);

    private static final byte[] CHARSET_BYTE_ARRAY = new byte[]{'c', 'h', 'a', 'r', 's', 'e', 't'};
    private static final int LENGTH = CHARSET_BYTE_ARRAY.length;
    private static final List<String> ignoredProtocol = new ArrayList<>();
    private static final Map<String, String> protocolTransfer = new HashMap<>();

    static{
        //@Date:2020/11/9 - @Author:XWB - @Msg: 获取不正常的协议名称，以便过滤
        Constant constant = new Constant();
        constant.setType(ConstKit.HTTP_COLLECT_PROTOCOL_IGNORE);
        List<Constant> constantList = SqlFactory.find(constant);
        if(!constantList.isEmpty()){
            ignoredProtocol.addAll(constantList.stream().map(Constant::getValue).filter(s -> !Strkit.isEmpty(s))
                    .flatMap(s -> Arrays.stream(s.split(","))).collect(Collectors.toList()));
        }
        constant.setType(ConstKit.HTTP_COLLECT_PROTOCOL_TRANSFER);
        constantList = SqlFactory.find(constant);
        if(!constantList.isEmpty()){
            protocolTransfer.putAll(constantList.stream().map(Constant::getValue).filter(s -> !Strkit.isEmpty(s))
                    .flatMap(s -> Arrays.stream(s.split(","))).map(s -> s.split(":"))
                    .filter(ss -> ss.length == 2).collect(Collectors.toMap(ss -> ss[0], ss->ss[1], (k1,k2)->k2)));
        }
    }
    private final NetworkUri networkUri;

    HttpCollectCallable(NetworkUri networkUri){
        this.networkUri = networkUri;
    }

    @Override
    public List<NetworkUri> call(){
        HttpURLConnection httpURLConnection = null;
        NetworkUri newNetworkUri = new NetworkUri();
        newNetworkUri.setId(this.networkUri.getId());
        try{
            URL url = new URL(this.networkUri.getUri());
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
                    return withResponse(byteArrayOutputStream.toByteArray(), url, this.networkUri, newNetworkUri);
                }
            }else{
                newNetworkUri.setMessage(httpURLConnection.getResponseMessage());
            }
            newNetworkUri.setStatus(CodeKit.COMPLETE);
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
            SqlFactory.update(newNetworkUri);
        }
        return null;
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

    /**
     * uri中是否带协议
     * @param uri uri
     * @return true：带协议，false：不带协议
     */
    private boolean isWithSchema(String uri){
        return uri.matches("^[^:]+:.+$");
    }

    private String transScheme(URI uri){
        String scheme = uri.getScheme();
        if(scheme.matches("^[htps]+:")){
            return "http";
        }
        return protocolTransfer.getOrDefault(uri.getScheme(), uri.getScheme());
    }

    /**
     * 校验协议是否无效
     * @param url url
     * @return 校验结果，true：无效，false：有效
     */
    private boolean invalidScheme(URL url){
        return !ignoredProtocol.isEmpty() && ignoredProtocol.contains(url.getProtocol().toLowerCase());
    }

    /**
     * 校验协议是否无效
     * @param uri uri
     * @return 校验结果，true：无效，false：有效
     */
    private boolean invalidScheme(URI uri){
        return !ignoredProtocol.isEmpty() && ignoredProtocol.contains(uri.getScheme());
    }
    /**
     * 解析返回的信息
     * @param bytes 网页信息
     * @param url url
     * @param origin 原始的networkUri
     */
    private List<NetworkUri> withResponse(byte[] bytes, URL url, NetworkUri origin, NetworkUri newNetworkUri){
        String charset = getCharset(bytes);
        String msg;
        try {
            msg = new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
            return null;
        }
        List<NetworkUri> networkUriList = new ArrayList<>();
        Pattern pattern = Pattern.compile("<title>(.+?)</title>");
        Matcher matcher = pattern.matcher(msg);
        String title = null;
        if(matcher.find()){
            title = matcher.group(1);
            newNetworkUri.setTitle(title);
        }
        newNetworkUri.setCharset(charset);
        //XWB-2020/9/21- 解析路径
        pattern = Pattern.compile("<a[^>]+href=['\"]?([^'\"\\s><]+)[^><]*>");
        matcher = pattern.matcher(msg);
        NetworkUri tmp = new NetworkUri();
        List<String> uriList = new ArrayList<>();
        NetworkUri networkUri;
        while(matcher.find()){
            String string = matcher.group(1);
            if(uriList.contains(string)){
                continue;
            }
            uriList.add(string);
            networkUri = new NetworkUri();
            networkUri.setLevel(origin.getLevel() + 1);
            networkUri.setHttpCode(0);
            networkUri.setContentLength(0L);
            networkUri.setStatus(0);
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
                        String scheme = this.transScheme(uri);
                        networkUri.setUri(uri.toString().replaceFirst("^" + uri.getScheme(), scheme));
                        networkUri.setProtocol(uri.getScheme());
                        if(!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")){
                            networkUri.setStatus(CodeKit.COMPLETE);//如果不是http，就直接完成
                            //XWB-2020/9/22- 完成时，将当前页的标题赋值给它
                            networkUri.setTitle(title);
                        }
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
            if(SqlFactory.find(tmp).isEmpty()){
                networkUri.setParentId(origin.getId());
                if(origin.getTopParentId() == 0){
                    networkUri.setTopParentId(origin.getId());
                }else{
                    networkUri.setTopParentId(origin.getTopParentId());
                }

                networkUriList.add(networkUri);
            }
        }
        return networkUriList;
    }

    private void withHttpUrl(URL url, NetworkUri networkUri){
        networkUri.setProtocol(url.getProtocol());
        networkUri.setUri(url.getProtocol() + "://" + url.getAuthority() + url.getFile());
        if(url.getHost().endsWith(".gov")){
            //XWB-2020/9/21- 如果是政府网站，直接完成
            networkUri.setStatus(CodeKit.GOV_NET);
        }
    }
}
