package com.xiaobin.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * sql生成类
 */
public class SqlFactory {
    private static final String MODEL_PACKAGE = "com.xiaobin.sql.model";
    private static final Logger logger = LoggerFactory.getLogger(SqlFactory.class);

    private static final ConcurrentMap<String, DbTable> DB_TABLE_CONCURRENT_MAP = new ConcurrentHashMap<>();

    static{
        init();
    }

    /**
     * 初始化数据库表对象
     */
    private static void init(){
        Set<Class<?>> classSet = new HashSet<>();
        try {
            Enumeration<URL> urlEnumeration = Thread.currentThread().getContextClassLoader().getResources(MODEL_PACKAGE.replaceAll("\\.", "/"));
            while(urlEnumeration.hasMoreElements()){
                URL url = urlEnumeration.nextElement();
                String protocol = url.getProtocol();
                if("file".equals(protocol)){
                    loadFromFile(classSet, url.getPath());
                }else if("jar".equals(protocol)){
                    loadFromJar(classSet, url);
                }else{
                    if(logger.isWarnEnabled()){
                        logger.warn("不识别的协议: {}, url={}", protocol, url.toString());
                    }
                }
            }
        } catch (IOException | ClassNotFoundException ioException) {
            if(logger.isErrorEnabled()){
                logger.error(ioException.getMessage(), ioException);
            }
            throw new RuntimeException("error in scan package: " + MODEL_PACKAGE);
        }

        if(classSet.isEmpty()){
            if(logger.isDebugEnabled()){
                logger.debug("class empty in package: {}", MODEL_PACKAGE);
            }
            return;
        }

        //XWB-2020/9/17- 解析成数据库类
        for(Class<?> clazz: classSet){
            String name = clazz.getName();
            DbTable dbTable = new DbTable();
            dbTable.load(clazz);
            DB_TABLE_CONCURRENT_MAP.put(name, dbTable);
        }
    }

    private static <T> DbTable getDbTable(T t){
        assert t != null;
        String name = t.getClass().getName();
        DbTable dbTable = DB_TABLE_CONCURRENT_MAP.get(name);
        if(dbTable == null){
            throw new RuntimeException("没有维护对象: " + name);
        }
        return dbTable;
    }
    /**
     * insert sql
     * @param t 原数据
     * @param <T> 泛型
     * @return SqlObj对象，包括sql和参数
     */
    public static <T> int insert(T t){
        DbTable dbTable = getDbTable(t);
        Map<String, ColumnMethod> methodMap = dbTable.getColumnMethodMap();
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder("insert into ")
                .append(dbTable.getTableName())
                .append("(");
        for(Map.Entry<String, ColumnMethod> entry: methodMap.entrySet()){
            ColumnMethod columnMethod = entry.getValue();
            try {
                Object object = columnMethod.getGetMethod().invoke(t);
                if(object != null){
                    stringBuilder.append(columnMethod.getName()).append(",");
                    valueList.add(object);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                if(logger.isErrorEnabled()){
                    logger.error(e.getMessage(), e);
                }
            }
        }
        if(valueList.isEmpty()){
            throw new RuntimeException("insert 值为空");
        }else{
            stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), ")")
                    .append(" values(")
                    .append(IntStream.range(0, valueList.size()).mapToObj(i -> "?").collect(Collectors.joining(",")))
                    .append(")");
            return exec(stringBuilder.toString(), valueList.toArray());
        }
    }

    /**
     * update sql
     * @param t 原数据
     * @param <T> 泛型
     * @return SqlObj
     */
    public static <T> int update(T t){
        DbTable dbTable = getDbTable(t);
        Map<String, ColumnMethod> methodMap = dbTable.getColumnMethodMap();
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder("update ")
                .append(dbTable.getTableName())
                .append(" set");
        for(Map.Entry<String, ColumnMethod> entry: methodMap.entrySet()){
            ColumnMethod columnMethod = entry.getValue();
            if(dbTable.getIdList().contains(columnMethod.getName())){
                continue;
            }
            Object object = getValue(columnMethod.getGetMethod(), t);
            if(object != null){
                stringBuilder.append(" ").append(columnMethod.getName()).append(" = ?,");
                valueList.add(object);
            }
        }
        stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), "").append(" where 1 = 1");
        withIdWhere(stringBuilder, valueList, dbTable, t);
        return exec(stringBuilder.toString(), valueList.toArray());
    }

    /**
     * 删除sql
     * @param t 原数据
     * @param <T> 泛型
     * @return SqlObj
     */
    public static <T> int delete(T t){
        DbTable dbTable = getDbTable(t);
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder("delete from ")
                .append(dbTable.getTableName())
                .append(" where 1 = 1");
        withIdWhere(stringBuilder, valueList, dbTable, t);
        return exec(stringBuilder.toString(), valueList.toArray());
    }

    /**
     * 通过id查询结果
     * @param t 原对象
     * @param object id值，如果为null，则从t中查询id值
     * @param <T> 泛型
     * @return 结果对象
     */
    public static <T> T findById(T t, Object object){
        DbTable dbTable = getDbTable(t);
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder("select * from ").append(dbTable.getTableName()).append(" where ");
        if(dbTable.getIdList().size() == 1){
            stringBuilder.append(dbTable.getIdList().get(0)).append(" = ?");
            valueList.add(object);
        }else{
            //XWB-2020/9/18- 多个id值，从t中获取信息
            stringBuilder.append("1 = 1");
            withIdWhere(stringBuilder, valueList, dbTable, t);
        }
        return execQuery(resultSet -> {
            try {
                if(resultSet.next()){
                    return getBeanFromResultSet(dbTable, resultSet);
                }
            } catch (SQLException sqlException) {
                if(logger.isErrorEnabled()){
                    logger.error("{}:{}", sqlException.getSQLState(), sqlException.getMessage(), sqlException);
                }
            }
            return null;
        }, stringBuilder.toString(), valueList.toArray());
    }

    /**
     * 查询列表
     * @param t 原数据
     * @param <T> 泛型
     * @return 查询list
     */
    public static <T> List<T> find(T t){
        DbTable dbTable = getDbTable(t);
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        queryInfo(t, dbTable, stringBuilder, valueList);
        return execQuery(resultSet -> getList(dbTable, resultSet), stringBuilder.toString(), valueList.toArray());
    }

    /**
     * 分页查询
     * @param t 原数据
     * @param start 起始
     * @param end 结束
     * @param <T> 泛型
     * @return Page
     */
    public static <T> Page<T> page(T t, int start, int end){
        DbTable dbTable = getDbTable(t);
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        queryInfo(t, dbTable, stringBuilder, valueList);
        Page<T> page = new Page<>();
        page.setStart(start);
        page.setEnd(end);
        //XWB-2020/9/18- 查询总条数
        String sql = stringBuilder.toString();
        String totalSql = "select count(*) as count from (" + sql + " ) a";
        Object[] objects = valueList.toArray();
        long total = execQuery(resultSet -> {
            try {
                if(resultSet.next()){
                    return resultSet.getLong("count");
                }
            } catch (SQLException sqlException) {
                if(logger.isErrorEnabled()){
                    logger.error("{}:{}", sqlException.getSQLState(), sqlException.getMessage(), sqlException);
                }
            }
            return 0L;
        }, totalSql, objects);
        page.setTotal(total);
        if(total > 0){
            //XWB-2020/9/18- 查询明细记录
            page.setList(execQuery(resultSet -> getList(dbTable, resultSet), sql, objects));
        }
        return page;
    }

    private static <T> void queryInfo(T t, DbTable dbTable, StringBuilder stringBuilder, List<Object> valueList){
        stringBuilder.append("select * from ").append(dbTable.getTableName()).append(" where 1 = 1");
        for(Map.Entry<String, ColumnMethod> entry: dbTable.getColumnMethodMap().entrySet()){
            ColumnMethod columnMethod = entry.getValue();
            Object object = getValue(columnMethod.getGetMethod(), t);
            if(object != null){
                stringBuilder.append(" and ").append(columnMethod.getName()).append(" = ?");
                valueList.add(object);
            }
        }
    }
    private static <T> List<T> getList(DbTable dbTable, ResultSet resultSet){
        List<T> list = new ArrayList<>();
        if(resultSet != null){
            try {
                while(resultSet.next()){
                    T tt = getBeanFromResultSet(dbTable, resultSet);
                    list.add(tt);
                }
            } catch (SQLException sqlException) {
                if(logger.isErrorEnabled()){
                    logger.error("{}:{}", sqlException.getSQLState(), sqlException.getMessage(), sqlException);
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getBeanFromResultSet(DbTable dbTable, ResultSet resultSet){
        T newT = null;
        try {
            newT = (T) dbTable.getClazz().newInstance();
            for(Map.Entry<String, ColumnMethod> entry: dbTable.getColumnMethodMap().entrySet()){
                Object value = getValueFromResultSet(entry.getValue().getType(), entry.getValue().getName(), resultSet);
                setValue(entry.getValue().getSetMethod(), newT, value);
            }
        } catch (IllegalAccessException | InstantiationException sqlException) {
            if(logger.isErrorEnabled()){
                logger.error(sqlException.getMessage(), sqlException);
            }
        }
        return newT;
    }

    private static Object getValueFromResultSet(Class<?> type, String columnName, ResultSet resultSet){
        try {
            if(type.isPrimitive()){
                switch(type.getName()){
                    case "int":
                        return resultSet.getInt(columnName);
                    case "byte":
                        return resultSet.getByte(columnName);
                    case "char":
                        return (char)resultSet.getInt(columnName);
                    case "float":
                        return resultSet.getFloat(columnName);
                    case "double":
                        return resultSet.getDouble(columnName);
                    case "boolean":
                        return resultSet.getBoolean(columnName);
                    case "short":
                        return resultSet.getShort(columnName);
                    case "long":
                        return resultSet.getLong(columnName);
                }
            }else{
                switch(type.getName()){
                    case "java.sql.Date":
                        return resultSet.getDate(columnName);
                    case "java.util.Date":
                        Date date = resultSet.getDate(columnName);
                        if(date != null){
                            return new java.util.Date(date.getTime());
                        }
                        return null;
                    case "java.time.LocalDate":
                        Date localDate = resultSet.getDate(columnName);
                        if(localDate != null){
                            return localDate.toLocalDate();
                        }
                        return null;
                    case "java.sql.Timestamp":
                        return resultSet.getTimestamp(columnName);
                    case "java.sql.Time":
                        return resultSet.getTime(columnName);
                    case "java.sql.Blob":
                        return resultSet.getBlob(columnName);
                    case "java.sql.Clob":
                        return resultSet.getClob(columnName);
                    case "java.sql.NClob":
                        return resultSet.getNClob(columnName);
                    case "java.lang.Long":
                        return resultSet.getLong(columnName);
                    case "java.lang.Integer":
                        return resultSet.getInt(columnName);
                    case "java.math.BigDecimal":
                        return resultSet.getBigDecimal(columnName);
                    default:
                        return resultSet.getString(columnName);
                }
            }
        } catch (SQLException sqlException) {
            if(logger.isErrorEnabled()){
                logger.error("{}:{}", sqlException.getSQLState(), sqlException.getMessage(), sqlException);
            }
        }
        return null;
    }

    private static <T> Object getValue(Method method, T t){
        try {
            return method.invoke(t);
        } catch (IllegalAccessException | InvocationTargetException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
            return null;
        }
    }

    private static <T> void setValue(Method method, T t, Object value){
        try {
            method.invoke(t, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            if(logger.isErrorEnabled()){
                logger.error(e.getMessage(), e);
            }
        }
    }

    private static <T> T execQuery(Function<ResultSet, T> function, String sql, Object[] objects){
        Dao2 dao2 = new Dao2();
        return dao2.find(function, sql, objects);
    }

    private static int exec(String sql, Object[] objects){
        Dao2 dao2 = new Dao2();
        return dao2.exec(sql, objects);
    }
    /**
     * 添加where的id条件
     * @param <T> 泛型
     * @param stringBuilder stringBuilder
     */
    private static <T> void withIdWhere(StringBuilder stringBuilder, List<Object> valueList, DbTable dbTable, T t){
        if(dbTable.getIdList().isEmpty()){
            throw new RuntimeException("id list 为空");
        }
        int index = 0;
        String id = dbTable.getIdList().get(index);
        Object object = getValue(dbTable.getColumnMethodMap().get(id).getGetMethod(), t);
        if(object == null){
            throw new RuntimeException("id["+id+"]值为空: ");
        }
        for(; index < dbTable.getIdList().size(); index++){
            id = dbTable.getIdList().get(index);
            object = getValue(dbTable.getColumnMethodMap().get(id).getGetMethod(), t);
            if(object == null){
                //强制所有id都必须有值
                throw new RuntimeException("id["+id+"]值为空: ");
            }
            stringBuilder.append(" and ").append(dbTable.getColumnMethodMap().get(id).getName()).append(" = ?");
            valueList.add(object);
        }
    }

    private static void loadFromJar(Set<Class<?>> classSet, URL url) throws ClassNotFoundException, IOException {
        String directoryName = MODEL_PACKAGE.replaceAll("\\.", "/");
        JarFile jarFile = ((JarURLConnection)url.openConnection()).getJarFile();
        Enumeration<JarEntry> jarEntries = jarFile.entries();
        ClassLoader classLoader = new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return super.loadClass(name);
            }
        };

        while(jarEntries.hasMoreElements()){
            JarEntry jarEntry = jarEntries.nextElement();
            String name = jarEntry.getName();
            if(name.startsWith("/")){
                name = name.substring(1);
            }
            if(name.startsWith(directoryName) && name.endsWith(".class") && !jarEntry.isDirectory()){
                String className = name.substring(name.lastIndexOf("/"), name.lastIndexOf("."));
                classSet.add(classLoader.loadClass(MODEL_PACKAGE + "." + className));
            }
        }
    }

    private static void loadFromFile(Set<Class<?>> classSet, String path) throws ClassNotFoundException {
        File file = new File(path);
        ClassLoader classLoader = new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return super.loadClass(name);
            }
        };
        if(file.isFile()){
            String name = file.getName();
            if(name.endsWith(".class")){
                name = name.substring(0, name.lastIndexOf("."));
            }
            classSet.add(classLoader.loadClass(MODEL_PACKAGE + "." + name));
        }else{
            //XWB-2020/9/17- 只扫描一层model
            File[] files = file.listFiles();
            assert files != null;
            for(File tmp: files){
                if(tmp.isFile() && tmp.getName().endsWith(".class")){
                    String name = tmp.getName().substring(0, tmp.getName().lastIndexOf("."));
                    classSet.add(classLoader.loadClass(MODEL_PACKAGE + "." + name));
                }
            }
        }
    }
}
