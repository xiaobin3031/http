package com.xiaobin.video;

import com.xiaobin.video.model.Mp4File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 参考资料 http://xhelmboyx.tripod.com/formats/mp4-layout.txt
 */
public class Mp4 {

    private static final Logger logger = LoggerFactory.getLogger(Mp4.class);
    private static final String FTYPE = "ftyp";
    private static final String MDAT = "mdat";
    private static final String FREE = "free";
    private static final String SKIP = "skip";
    private static final String WIDE = "wide";

    private Mp4File mp4File;
    private byte[] bytes;
    private int start;

    public static void main(String[] args) {
        Mp4 main = new Mp4();
        main.start();
    }

    private void start(){
        File file = new File("/Users/lixiaolin/Documents/xiaobin/video/fire.mp4");
        assert file.exists();
        if(logger.isDebugEnabled()){
            logger.debug("文件大小: {}", file.length());
        }
        if(file.length() > Integer.MAX_VALUE){
            if(logger.isErrorEnabled()){
                logger.error("{} > {},文件过大，不解析", file.length(), Integer.MAX_VALUE);
            }
            throw new RuntimeException("文件过大，不解析");
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try(FileInputStream fileInputStream = new FileInputStream(file)){
            byte[] bytes = new byte[1024];
            while(fileInputStream.read(bytes) != -1){
                byteArrayOutputStream.write(bytes);
            }
        } catch (IOException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }
        bytes = byteArrayOutputStream.toByteArray();
        mp4File = new Mp4File(file.length());
        this.start = 0;

        byte[] byte4 = new byte[4];
        while(this.start < file.length() - 1){
            System.arraycopy(bytes, this.start + 4, byte4, 0, byte4.length);
            String type = new String(byte4, StandardCharsets.UTF_8);
            switch(type){
                case FTYPE:
                    ofFtyp();
                    break;
                case MDAT:
                    ofMdat();
                    break;
                case FREE:
                    ofFree();
                    break;
                case SKIP:
                    ofSkip();
                    break;
                case WIDE:
                    ofWide();
                    break;
                default:
                    if(logger.isWarnEnabled()){
                        logger.warn("unknown type: {}", type);
                    }
                    break;
            }
        }

        System.out.println(mp4File);
    }

    private void readFromBytes(byte[] array){
        System.arraycopy(this.bytes, this.start, array, 0, array.length);
    }
    private void ofFtyp(){
        byte[] byte4 = new byte[4];
        readFromBytes(byte4);
        this.mp4File.setFtypOffset(bytesToLong(byte4));
        this.start += 8;
        readFromBytes(byte4);
        this.mp4File.setMajorBrand(new String(byte4, StandardCharsets.UTF_8));
        this.start += 4;
        readFromBytes(byte4);
        this.mp4File.setMajorBrandVersion(bytesToLong(byte4));
        this.start += 4;
        long end = this.mp4File.getFtypOffset() - 4;
        StringBuilder stringBuilder = new StringBuilder();
        while(this.start < end){
            readFromBytes(byte4);
            stringBuilder.append(new String(byte4, StandardCharsets.UTF_8)).append(",");
            this.start += 4;
        }
        this.mp4File.setCompatibleBrands(stringBuilder.toString().split(","));
    }

    private void ofMdat(){
        byte[] byte4 = new byte[4];
        readFromBytes(byte4);
        long offset = bytesToLong(byte4);
        //todo 如果offset等于1，需要扩展到2^64，详情见参考资料
        this.mp4File.setMdatOffset(offset);
        this.start += 8;
        byte4 = new byte[(int)offset];
        readFromBytes(byte4);
        this.mp4File.setMdat(byte4);
        this.start += offset;
    }

    private void ofFree(){
        byte[] byte4 = new byte[4];
        readFromBytes(byte4);
        this.mp4File.setFreeOffset(bytesToLong(byte4));
        this.start += 8;
    }

    private void ofSkip(){
        byte[] byte4 = new byte[4];
        readFromBytes(byte4);
        this.mp4File.setSkipOffset(bytesToLong(byte4));
        this.start += 8;
    }

    private void ofWide(){
        byte[] byte4 = new byte[4];
        readFromBytes(byte4);
        this.mp4File.setWideOffset(bytesToLong(byte4));
        this.start += 8;
    }

    private long bytesToLong(byte[] bytes){
        long l = 0;
        int count = bytes.length - 1;
        for(int i=0;i< bytes.length; i++){
            byte b = bytes[i];
            if(b < 0){
                b += 256;
            }
            int shift = (count - i) * 8;
            l += (b << shift);
        }
        return l;
    }
}
