package com.xiaobin.model;

import com.xiaobin.sql.annotation.ID;
import com.xiaobin.sql.annotation.Table;

/**
 * Created by XWB on 2020-11-09.
 */
@Table
public class Constant{

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
