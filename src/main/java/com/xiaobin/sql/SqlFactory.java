package com.xiaobin.sql;

import com.xiaobin.sql.annotation.ID;
import com.xiaobin.sql.annotation.Table;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
        Map<String, ColumnMethod> methodMap = dbTable.columnMethodMap;
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder("insert into ")
                .append(dbTable.tableName)
                .append("(");
        for(Map.Entry<String, ColumnMethod> entry: methodMap.entrySet()){
            ColumnMethod columnMethod = entry.getValue();
            try {
                Object object = columnMethod.getMethod.invoke(t);
                if(object != null){
                    stringBuilder.append(columnMethod.name).append(",");
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
        Map<String, ColumnMethod> methodMap = dbTable.columnMethodMap;
        List<Object> valueList = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder("update ")
                .append(dbTable.tableName)
                .append(" set");
        for(Map.Entry<String, ColumnMethod> entry: methodMap.entrySet()){
            ColumnMethod columnMethod = entry.getValue();
            if(dbTable.idList.contains(columnMethod.name)){
                continue;
            }
            Object object = getValue(columnMethod.getMethod, t);
            if(object != null){
                stringBuilder.append(" ").append(columnMethod.name).append(" = ?");
                valueList.add(object);
            }
        }
        stringBuilder.append(" where 1 = 1");
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
                .append(dbTable.tableName)
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
        StringBuilder stringBuilder = new StringBuilder("select * from ").append(dbTable.tableName).append(" where ");
        if(dbTable.idList.size() == 1){
            stringBuilder.append(dbTable.idList.get(0)).append(" = ?");
            valueList.add(object);
        }else{
            //XWB-2020/9/18- 多个id值，从t中获取信息
            stringBuilder.append("1 = 1");
            withIdWhere(stringBuilder, valueList, dbTable, t);
        }
        ResultSet resultSet = execQuery(stringBuilder.toString(), valueList.toArray());
        try {
            if(resultSet.next()){
                return getBeanFromResultSet(t, dbTable, resultSet);
            }
        } catch (SQLException sqlException) {
            if(logger.isErrorEnabled()){
                logger.error("{}:{}", sqlException.getSQLState(), sqlException.getMessage(), sqlException);
            }
        }
        return null;
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
        StringBuilder stringBuilder = new StringBuilder("select * from ").append(dbTable.tableName).append(" where 1 = 1");
        for(Map.Entry<String, ColumnMethod> entry: dbTable.columnMethodMap.entrySet()){
            ColumnMethod columnMethod = entry.getValue();
            Object object = getValue(columnMethod.getMethod, t);
            if(object != null){
                stringBuilder.append(" and ").append(columnMethod.name).append(" = ?");
                valueList.add(object);
            }
        }
        ResultSet resultSet = execQuery(stringBuilder.toString(), valueList.toArray());
        List<T> list = new ArrayList<>();
        try {
            while(resultSet.next()){
                T tt = getBeanFromResultSet(t, dbTable, resultSet);
                list.add(tt);
            }
        } catch (SQLException sqlException) {
            if(logger.isErrorEnabled()){
                logger.error("{}:{}", sqlException.getSQLState(), sqlException.getMessage(), sqlException);
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getBeanFromResultSet(T t, DbTable dbTable, ResultSet resultSet){
        T newT = null;
        try {
            newT = (T) Class.forName(t.getClass().getName()).newInstance();
            for(Map.Entry<String, ColumnMethod> entry: dbTable.columnMethodMap.entrySet()){
                Object value = getValueFromResultSet(entry.getValue().type, entry.getValue().name, resultSet);
                setValue(entry.getValue().setMethod, t, value);
            }
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException sqlException) {
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

    private static ResultSet execQuery(String sql, Object[] objects){
        Dao2 dao2 = new Dao2();
        return dao2.find(sql, objects);
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
        if(dbTable.idList.isEmpty()){
            throw new RuntimeException("id list 为空");
        }
        for(String id: dbTable.idList){
            Object object = getValue(dbTable.columnMethodMap.get(id).getMethod, t);
            if(object == null){
                //强制所有id都必须有值
                throw new RuntimeException("id["+id+"]值为空: ");
            }
            stringBuilder.append(" and ").append(id).append(" = ?");
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

    /**
     * 数据库类
     */
    private static class DbTable{
        /* 表名 */
        private String tableName;

        private final List<String> idList = new ArrayList<>();

        private final Map<String, ColumnMethod> columnMethodMap = new HashMap<>();

        /**
         * 加载类信息
         * @param clazz 对象
         */
        private void load(Class<?> clazz){
            Table table = clazz.getDeclaredAnnotation(Table.class);
            if(table != null){
                String tableName = table.name();
                if(Strkit.isEmpty(tableName)){
                    this.tableName = toLowerCase(clazz.getName());
                }else{
                    this.tableName = tableName;
                }
                Field[] fields = clazz.getDeclaredFields();
                for(Field field: fields){
                    String name = field.getName();
                    String methodSuffixName = name.substring(0, 1).toUpperCase() + name.substring(1);
                    Method getMethod = getAMethod(clazz, "get" + methodSuffixName, null);
                    if(getMethod == null){
                        getMethod = getAMethod(clazz, "is" + methodSuffixName, null);
                    }
                    if(getMethod == null){
                        continue;
                    }
                    Method setMethod = getAMethod(clazz, "set" + methodSuffixName, field.getType());
                    if(setMethod == null){
                        continue;
                    }
                    ColumnMethod columnMethod = new ColumnMethod(
                            field.getName(),
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

        private Method getAMethod(Class<?> clazz, String methodName, Class<?> parameters){
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

    private static class ColumnMethod{
        /* 列名 */
        private final String name;
        /* 列类型 */
        private final Class<?> type;

        private final Method getMethod;

        private final Method setMethod;

        public ColumnMethod(String name, Class<?> type, Method getMethod, Method setMethod) {
            this.name = name;
            this.type = type;
            this.getMethod = getMethod;
            this.setMethod = setMethod;
        }
    }
}
