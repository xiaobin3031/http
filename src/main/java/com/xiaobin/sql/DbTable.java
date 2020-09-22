package com.xiaobin.sql;

import com.xiaobin.sql.annotation.ID;
import com.xiaobin.sql.annotation.Table;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbTable {

    DbTable(){}
    private static final Logger logger = LoggerFactory.getLogger(DbTable.class);
    /* 表名 */
    private String tableName;

    private Class<?> clazz;

    private final List<String> idList = new ArrayList<>();

    private final Map<String, ColumnMethod> columnMethodMap = new HashMap<>();

    String getTableName() {
        return tableName;
    }

    Class<?> getClazz() {
        return clazz;
    }

    List<String> getIdList() {
        return idList;
    }

    Map<String, ColumnMethod> getColumnMethodMap() {
        return columnMethodMap;
    }

    /**
     * 加载类信息
     * @param clazz 对象
     */
    void load(Class<?> clazz){
        this.clazz = clazz;
        Table table = clazz.getDeclaredAnnotation(Table.class);
        if(table != null){
            String tableName = table.name();
            if(Strkit.isEmpty(tableName)){
                this.tableName = toLowerCase(clazz.getSimpleName());
            }else{
                this.tableName = tableName;
            }
            Field[] fields = clazz.getDeclaredFields();
            for(Field field: fields){
                String name = field.getName();
                String methodSuffixName = name.substring(0, 1).toUpperCase() + name.substring(1);
                Method getMethod = getAMethod(clazz, "get" + methodSuffixName);
                if(getMethod == null){
                    getMethod = getAMethod(clazz, "is" + methodSuffixName);
                }
                if(getMethod == null){
                    continue;
                }
                Method setMethod = getAMethod(clazz, "set" + methodSuffixName, field.getType());
                if(setMethod == null){
                    continue;
                }
                ColumnMethod columnMethod = new ColumnMethod(
                        toLowerCase(field.getName()),
                        field.getType(),
                        getMethod,
                        setMethod);
                this.columnMethodMap.put(field.getName(), columnMethod);

                ID id = field.getDeclaredAnnotation(ID.class);
                if(id != null){
                    idList.add(field.getName());
                }
            }
        }
    }

    /**
     * 获取不带参数的方法
     * @param clazz 类
     * @param methodName 方法名称
     * @return 方法
     */
    Method getAMethod(Class<?> clazz, String methodName){
        try{
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage());
            }
            return null;
        }
    }

    /**
     * 获取带参数的方法
     * @param clazz 类
     * @param methodName 方法名
     * @param parameters 参数
     * @return 方法
     */
    Method getAMethod(Class<?> clazz, String methodName, Class<?>... parameters){
        try{
            return clazz.getDeclaredMethod(methodName, parameters);
        } catch (NoSuchMethodException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage());
            }
            return null;
        }
    }
    private String toLowerCase(String string){
        return string.substring(0, 1).toLowerCase() + Strkit.replaceAll(string.substring(1), "[A-Z]", o -> "_" + o.toLowerCase());
    }
}
