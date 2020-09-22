package com.xiaobin.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 字符集编码
 *
 * 国标码是汉字的国家标准编码，目前主要有GB2312、GBK、GB18030三种。
 * 1、GB2312编码方案于1980年发布，收录汉字6763个，采用双字节编码。
 * 2、GBK编码方案于1995年发布，收录汉字21003个，采用双字节编码。
 * 3、GB18030编码方案于2000年发布第一版，收录汉字27533个；2005年发布第二版，收录汉字70000余个，以及多种少数民族文字。GB18030采用单字节、双字节、四字节分段编码。
 *
 * 新版向下兼容旧版，也就是说GBK是在GB2312已有码位基础上增加新码位，GB18030是在GBK已有码位基础上增加新码位，各种编码方案中共有的字符编码相同。现在的中文信息处理应优先采用GB18030编码方案。
 */
public class CharsetKit {

    private static final Map<String, String> CHARSET_MAP = new HashMap<>();

    static {
        CHARSET_MAP.put("gb2312", "gb18030");
        CHARSET_MAP.put("gbk", "gb18030");
    }

    /**
     * 获取转换后的字符编码
     * @param charset 原字符编码
     * @return 转换后的字符编码
     */
    public static String getCharset(String charset){
        if(charset == null){
            return null;
        }
        return CHARSET_MAP.getOrDefault(charset.toLowerCase(), charset);
    }
}
