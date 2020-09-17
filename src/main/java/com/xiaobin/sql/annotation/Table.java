package com.xiaobin.sql.annotation;

import java.lang.annotation.*;

/**
 * 表名
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    String name() default "";
}
