package com.xiaobin.redis;

import com.xiaobin.conn.redis.RedisConnection;
import com.xiaobin.conn.redis.RedisObj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * redis操作类
 */
public class Redis {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);

    private static final byte[] CR_LF = new byte[]{'\r', '\n'};
    private static final byte LIST_PREFIX = '*';
    private static final byte BULK_STRING_PREFIX = '$';
    private static final byte SIMPLE_STRING_PREFIX = '+';
    private static final byte INT_PREFIX = ':';
    private static final byte ERROR_PREFIX = '-';

    /**
     * list转map
     * @param list list
     * @return map
     */
    private Map<String, String> toMap(List<Object> list){
        if(list == null){
            return null;
        }
        Map<String, String> map = new HashMap<>();
        if(!list.isEmpty()){
            int length = list.size();
            if(length % 2 != 0){
                length = length - 1;
            }
            for(int i=0;i<length; i++){
                map.put(String.valueOf(list.get(i++)), String.valueOf(list.get(i)));
            }
        }
        return map;
    }

    private List<Object> decodeList(InputStream inputStream, ByteArrayOutputStream byteArrayOutputStream) throws IOException{
        readFromInputStream(inputStream, byteArrayOutputStream);
        int count = Integer.parseInt(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
        if(count > -1){
            List<Object> list = new ArrayList<>();
            if(count > 0){
                for(int index=0;index<count;index++){
                    list.add(decode(inputStream, byteArrayOutputStream));
                }
            }
            return  list;
        }
        return null;
    }

    private Long decodeLong(InputStream inputStream, ByteArrayOutputStream byteArrayOutputStream) throws IOException{
        readFromInputStream(inputStream, byteArrayOutputStream);
        return Long.parseLong(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
    }

    private String decodeString(InputStream inputStream, ByteArrayOutputStream byteArrayOutputStream) throws IOException{
        readFromInputStream(inputStream, byteArrayOutputStream);
        int length = Integer.parseInt(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
        if(length == -1){
            return null;
        }else if(length == 0){
            return "";
        }else{
            byte[] bytes = new byte[length];
            if(inputStream.read(bytes) == -1){
                throw new RuntimeException("redis返回格式不正确");
            }
            byte[] bytes1 = new byte[2];
            if(inputStream.read(bytes1) == -1 || !Arrays.equals(bytes1, CR_LF)){
                throw new RuntimeException("redis返回格式不正确");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private void readFromInputStream(InputStream inputStream, ByteArrayOutputStream byteArrayOutputStream) throws IOException{
        byteArrayOutputStream.reset();
        int i;
        while((i = inputStream.read()) != -1 && (char)i != CR_LF[0]){
            byteArrayOutputStream.write(i);
        }
        char ch = (char)inputStream.read();
        if(ch != CR_LF[1]){
            throw new RuntimeException("redis返回格式不正确");
        }
    }
    private Object decode(InputStream inputStream, ByteArrayOutputStream byteArrayOutputStream) throws IOException {
        int i;
        char ch;
        i = inputStream.read();
        ch = (char)i;
        switch(ch){
            case LIST_PREFIX:
                return decodeList(inputStream, byteArrayOutputStream);
            case SIMPLE_STRING_PREFIX:
                readFromInputStream(inputStream, byteArrayOutputStream);
                return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
            case BULK_STRING_PREFIX:
                return decodeString(inputStream, byteArrayOutputStream);
            case INT_PREFIX:
                return decodeLong(inputStream, byteArrayOutputStream);
            case ERROR_PREFIX:
                String string = decodeString(inputStream, byteArrayOutputStream);
                if(string != null && string.contains(" ")){
                    throw new RuntimeException(string.substring(string.indexOf(" ") + 1));
                }else{
                    throw new RuntimeException(string);
                }
            default:
                throw new RuntimeException("unknown char: " + ch);
        }
    }

    private byte[] length2String(int length){
        return (length + "").getBytes(StandardCharsets.UTF_8);
    }

    public Object call(String... strings){
        if(strings == null || strings.length == 0){
            throw new RuntimeException("命令为空");
        }
        RedisObj redisObj = RedisConnection.getInstance().getConn();
        try{
            Socket socket = redisObj.getSocket();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(LIST_PREFIX);
            byteArrayOutputStream.write(length2String(strings.length));
            byteArrayOutputStream.write(CR_LF);
            for(String string: strings){
                byteArrayOutputStream.write(BULK_STRING_PREFIX);
                if(string == null){
                    byteArrayOutputStream.write(-1);
                }else{
                    byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                    byteArrayOutputStream.write(length2String(bytes.length));
                    byteArrayOutputStream.write(CR_LF);
                    byteArrayOutputStream.write(bytes);
                }
                byteArrayOutputStream.write(CR_LF);
            }
            if(logger.isDebugEnabled()){
                logger.debug(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
            }

            socket.getOutputStream().write(byteArrayOutputStream.toByteArray());
            socket.getOutputStream().flush();

            byteArrayOutputStream.reset();
            InputStream inputStream = socket.getInputStream();
            Object object = decode(inputStream, byteArrayOutputStream);
            RedisConnection.getInstance().inPool(redisObj);
            return object;
        } catch (Exception ioException) {
            if(logger.isErrorEnabled()){
                logger.error(ioException.getMessage(), ioException);
            }
            //XWB-2020/9/25- 报错后，将连接关闭
            RedisConnection.getInstance().close(redisObj);
        }
        return null;
    }
}
