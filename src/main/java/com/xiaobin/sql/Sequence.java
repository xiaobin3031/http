package com.xiaobin.sql;

import java.util.Map;

public class Sequence {

    private static final Dao2 DAO_2 = new Dao2();

    /**
     * 获取序列的下一个值
     * @param name 名称
     * @return 序列
     */
    public int nextval(String name){
        Map<String, Object> map = DAO_2.findOne("select nextval() as id");
        if(map == null || map.get("id") == null){
            return 0;
        }
        return Integer.parseInt(map.getOrDefault("id", "0").toString());
    }
}
