package com.xiaobin.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 替换所有
     * @param string 原数据
     * @param regExp 正则
     * @param function 转换函数
     * @return 转换后的字符串
     */
    public static String replaceAll(String string, String regExp, Function<String, String> function){
        if(isEmpty(string)){
            return string;
        }
        String newString = string;
        Pattern pattern = Pattern.compile(regExp);
        Matcher matcher = pattern.matcher(string);
        while(matcher.find()){
            newString = newString.replaceFirst(matcher.group(), function.apply(matcher.group()));
        }
        return newString;
    }
}
