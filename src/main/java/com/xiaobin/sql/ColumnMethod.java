package com.xiaobin.sql;

import java.lang.reflect.Method;

public class ColumnMethod {

    /* 列名 */
    private final String name;
    /* 列类型 */
    private final Class<?> type;

    private final Method getMethod;

    private final Method setMethod;

    ColumnMethod(String name, Class<?> type, Method getMethod, Method setMethod) {
        this.name = name;
        this.type = type;
        this.getMethod = getMethod;
        this.setMethod = setMethod;
    }

    String getName() {
        return name;
    }

    Class<?> getType() {
        return type;
    }

    Method getGetMethod() {
        return getMethod;
    }

    Method getSetMethod() {
        return setMethod;
    }
}
