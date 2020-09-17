package com.xiaobin.sql;

import com.xiaobin.sql.annotation.ID;
import com.xiaobin.sql.annotation.Table;
import com.xiaobin.util.Strkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    public static void main(String[] args) {
        init();
    }

    /**
     * 数据库类
     */
    private static class DbTable{
        /* 表名 */
        private String tableName;

        private String idSql = "";

        private String[] idNames;

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
                List<String> idList = new ArrayList<>();
                StringBuilder stringBuilder = new StringBuilder();
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
                    ColumnMethod columnMethod = new ColumnMethod();
                    columnMethod.setType(field.getType());
                    columnMethod.setName(field.getName());
                    columnMethod.setGetMethod(getMethod);
                    columnMethod.setSetMethod(setMethod);
                    this.columnMethodMap.put(field.getName(), columnMethod);

                    ID id = field.getDeclaredAnnotation(ID.class);
                    if(id != null){
                        idList.add(field.getName());
                        stringBuilder.append(" ").append(field.getName()).append(" = ?");
                    }
                }
                this.idSql = stringBuilder.toString();
                this.idNames = idList.toArray(new String[0]);
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

        private Map<String, ColumnMethod> getColumnMethodMap() {
            return columnMethodMap;
        }

        private String getIdSql() {
            return idSql;
        }

        private String getTableName() {
            return tableName;
        }

        private String[] getIdNames() {
            return idNames;
        }
    }

    private static class ColumnMethod{
        /* 列名 */
        private String name;
        /* 列类型 */
        private Class<?> type;

        private Method getMethod;

        private Method setMethod;

        private Class<?> getType() {
            return type;
        }

        private void setType(Class<?> type) {
            this.type = type;
        }

        private String getName() {
            return name;
        }

        private void setName(String name) {
            this.name = name;
        }

        private Method getGetMethod() {
            return getMethod;
        }

        private void setGetMethod(Method getMethod) {
            this.getMethod = getMethod;
        }

        private Method getSetMethod() {
            return setMethod;
        }

        private void setSetMethod(Method setMethod) {
            this.setMethod = setMethod;
        }
    }
}
