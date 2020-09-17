package com.xiaobin.sql.annotation;

import java.lang.annotation.*;

/**
 * 主键
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ID {

    String name() default "";
}
