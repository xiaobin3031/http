package com.xiaobin.video.model;

import java.util.HashMap;
import java.util.Map;

/**
 * mp4文件格式
 * 参考资料 http://xhelmboyx.tripod.com/formats/mp4-layout.txt
 */
public class Mp4File {

    private static final Map<String, String> typeDescription = new HashMap<>();
    static{
        typeDescription.put("isom", "ISO 14496-1 Base Media");
        typeDescription.put("iso2", "ISO 14496-12 Base Media");
        typeDescription.put("qt  ", "quicktime movie");
        typeDescription.put("avc1", "JVT AVC");
        typeDescription.put("mp41", "ISO 14496-1 vers. 1");
        typeDescription.put("mp42", "ISO 14496-1 vers. 2");
        typeDescription.put("3gp ", "3G MP4 profile");
        typeDescription.put("mmp4", "3G Mobile MP4");
        typeDescription.put("M4A ", "Apple AAC audio w/ iTunes info");
        typeDescription.put("M4P ", "AES encrypted audio");
        typeDescription.put("M4B ", "Apple audio w/ iTunes position");
        typeDescription.put("mp71", "ISO 14496-12 MPEG-7 meta data");
    }

    private final long length;//文件长度

    public Mp4File(long length){
        this.length = length;
    }
    /* ftyp的偏移 */
    private long ftypOffset;
    /* free的偏移 */
    private long freeOffset;
    /* skip的偏移 */
    private long skipOffset;
    /* wide的偏移 */
    private long wideOffset;
    /* 主要版本 */
    private String majorBrand;
    /* 主要版本号 */
    private long majorBrandVersion;
    /* 兼容版本 */
    private String[] compatibleBrands;
    /* mdat的偏移 */
    private long mdatOffset;
    /* mdat数据 */
    private byte[] mdat;

    public long getLength() {
        return length;
    }

    public long getFtypOffset() {
        return ftypOffset;
    }

    public void setFtypOffset(long ftypOffset) {
        this.ftypOffset = ftypOffset;
    }

    public long getFreeOffset() {
        return freeOffset;
    }

    public void setFreeOffset(long freeOffset) {
        this.freeOffset = freeOffset;
    }

    public long getSkipOffset() {
        return skipOffset;
    }

    public void setSkipOffset(long skipOffset) {
        this.skipOffset = skipOffset;
    }

    public long getWideOffset() {
        return wideOffset;
    }

    public void setWideOffset(long wideOffset) {
        this.wideOffset = wideOffset;
    }

    public String getMajorBrand() {
        return majorBrand;
    }

    public void setMajorBrand(String majorBrand) {
        this.majorBrand = majorBrand;
    }

    public long getMajorBrandVersion() {
        return majorBrandVersion;
    }

    public void setMajorBrandVersion(long majorBrandVersion) {
        this.majorBrandVersion = majorBrandVersion;
    }

    public String[] getCompatibleBrands() {
        return compatibleBrands;
    }

    public void setCompatibleBrands(String[] compatibleBrands) {
        this.compatibleBrands = compatibleBrands;
    }

    public long getMdatOffset() {
        return mdatOffset;
    }

    public void setMdatOffset(long mdatOffset) {
        this.mdatOffset = mdatOffset;
    }

    public byte[] getMdat() {
        return mdat;
    }

    public void setMdat(byte[] mdat) {
        this.mdat = mdat;
    }
}
