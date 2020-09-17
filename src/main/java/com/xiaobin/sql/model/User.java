package com.xiaobin.sql.model;

import com.xiaobin.sql.Dao;
import com.xiaobin.sql.annotation.Table;

@Table
public class User{

    private int id;

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    private static <T> void test2(T t){
        System.out.println(t.getClass().getName());
    }
    private static void test(Object object){
        System.out.println(object.toString());
        System.out.println(object);
    }

    public static void main(String[] args) {
        User user = new User();
        test(user);
        test2(user);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                '}';
    }
}
