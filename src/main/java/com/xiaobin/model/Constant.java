package com.xiaobin.model;

import com.xiaobin.sql.Dao;
import com.xiaobin.sql.annotation.ID;

/**
 * Created by XWB on 2020-11-09.
 */
public class Constant implements Dao {

    @ID
    private Integer id;
    private String type;
    private String value;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
