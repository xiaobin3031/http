package com.xiaobin.util;

public class Strkit {

    /**
     * 字符串为空
     * @param string string
     * @return true:
     */
    public static boolean isEmpty(String string){
        return string == null || string.trim().isEmpty();
    }

    /**
     * 首字母大写
     * @param string string
     * @return 首字母大写或者""
     */
    public static String upperAtFirst(String string){
        if(isEmpty(string)){
            return "";
        }
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

}
